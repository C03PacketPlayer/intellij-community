package com.intellij.settingsSync.config

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.settingsSync.*
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel

internal class SettingsSyncConfigurable : BoundConfigurable(message("title.settings.sync")),
                                          SettingsSyncEnabler.Listener,
                                          SettingsSyncStatusTracker.Listener {

  private lateinit var configPanel: DialogPanel
  private lateinit var enableButton: Cell<JButton>
  private lateinit var statusLabel: JLabel

  private val syncEnabler = SettingsSyncEnabler()

  init {
    syncEnabler.addListener(this)
    SettingsSyncStatusTracker.getInstance().addListener(this)
  }

  inner class LoggedInPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) =
      SettingsSyncAuthService.getInstance().addListener(object : SettingsSyncAuthService.Listener {
        override fun stateChanged() {
          listener(invoke())
          if (SettingsSyncAuthService.getInstance().isLoggedIn() && !SettingsSyncSettings.getInstance().syncEnabled) {
            syncEnabler.checkServerState()
          }
        }
      }, disposable!!)

    override fun invoke() = SettingsSyncAuthService.getInstance().isLoggedIn()
  }

  inner class EnabledPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      SettingsSyncEvents.getInstance().addEnabledStateChangeListener(object : SettingsSyncEnabledStateListener {
        override fun enabledStateChanged(syncEnabled: Boolean) {
          listener(invoke())
        }
      }, disposable!!)
    }

    override fun invoke() = SettingsSyncSettings.getInstance().syncEnabled

  }

  inner class SyncEnablerRunning : ComponentPredicate() {
    private var isRunning = false

    override fun addListener(listener: (Boolean) -> Unit) {
      syncEnabler.addListener(object : SettingsSyncEnabler.Listener {
        override fun serverRequestStarted() {
          updateRunning(listener, true)
        }

        override fun serverRequestFinished() {
          updateRunning(listener, false)
        }
      })
    }

    private fun updateRunning(listener: (Boolean) -> Unit, isRunning: Boolean) {
      this.isRunning = isRunning
      listener(invoke())
    }

    override fun invoke(): Boolean = isRunning
  }

  override fun createPanel(): DialogPanel {
    val categoriesPanel = SettingsSyncPanelFactory.createPanel(message("configurable.what.to.sync.label"))
    val authService = SettingsSyncAuthService.getInstance()
    configPanel = panel {
      val isSyncEnabled = LoggedInPredicate().and(EnabledPredicate())
      row {
        val statusCell = label("")
        statusCell.visibleIf(LoggedInPredicate())
        statusLabel = statusCell.component
        updateStatusInfo()
        label(message("sync.status.login.message")).visibleIf(LoggedInPredicate().not())
      }
      row {
        comment(message("settings.sync.info.message"), 80)
          .visibleIf(isSyncEnabled.not())
      }
      row {
        button(message("config.button.login")) {
          authService.login()
        }.visibleIf(LoggedInPredicate().not()).enabled(authService.isLoginAvailable())
        label(message("error.label.login.not.available")).component.apply {
          isVisible = !authService.isLoginAvailable()
          icon = AllIcons.General.Error
          foreground = JBColor.red
        }
        enableButton = button(message("config.button.enable")) {
          syncEnabler.checkServerState()
        }.visibleIf(LoggedInPredicate().and(EnabledPredicate().not())).enabledIf(SyncEnablerRunning().not())
        button(message("config.button.disable")) {
          LoggedInPredicate().and(EnabledPredicate())
          disableSync()
        }.visibleIf(isSyncEnabled)
      }
      row {
        cell(categoriesPanel)
          .visibleIf(LoggedInPredicate().and(EnabledPredicate()))
          .onApply { categoriesPanel.apply() }
          .onReset { categoriesPanel.reset() }
          .onIsModified { categoriesPanel.isModified() }
      }
    }
    return configPanel
  }

  override fun serverStateCheckFinished(state: ServerState) {
    when (state) {
      ServerState.FileNotExists -> showEnableSyncDialog(false)
      ServerState.UpToDate, ServerState.UpdateNeeded -> showEnableSyncDialog(true)
      is ServerState.Error -> {
        if (state != SettingsSyncEnabler.State.CANCELLED) {
          showError(enableButton.component, message("notification.title.update.error"), state.message)
        }
      }
    }
  }

  override fun updateFromServerFinished(result: UpdateResult) {
    when (result) {
      is UpdateResult.Success -> {
        SettingsSyncSettings.getInstance().syncEnabled = true
      }
      UpdateResult.NoFileOnServer -> {
        showError(enableButton.component, message("notification.title.update.error"), message("notification.title.update.no.such.file"))
      }
      is UpdateResult.Error -> {
        showError(enableButton.component, message("notification.title.update.error"), result.message)
      }
    }
    updateStatusInfo()
  }

  private fun showEnableSyncDialog(remoteSettingsFound: Boolean) {
    EnableSettingsSyncDialog.showAndGetResult(configPanel, remoteSettingsFound)?.let {
      reset()
      when (it) {
        EnableSettingsSyncDialog.Result.GET_FROM_SERVER -> syncEnabler.getSettingsFromServer()
        EnableSettingsSyncDialog.Result.PUSH_LOCAL -> {
          SettingsSyncSettings.getInstance().syncEnabled = true
          syncEnabler.pushSettingsToServer()
        }
      }
    }
  }

  companion object DisableResult {
    const val RESULT_CANCEL = 0
    const val RESULT_REMOVE_DATA_AND_DISABLE = 1
    const val RESULT_DISABLE = 2
  }

  private fun disableSync() {
    @Suppress("DialogTitleCapitalization")
    val result = Messages.showCheckboxMessageDialog( // TODO<rv>: Use AlertMessage instead
      message("disable.dialog.text"),
      message("disable.dialog.title"),
      arrayOf(Messages.getCancelButton(), message("disable.dialog.disable.button")),
      message("disable.dialog.remove.data.box"),
      false,
      1,
      1,
      Messages.getInformationIcon()
    ) { index: Int, checkbox: JCheckBox ->
      if (index == 1) {
        if (checkbox.isSelected) RESULT_REMOVE_DATA_AND_DISABLE else RESULT_DISABLE
      }
      else {
        RESULT_CANCEL
      }
    }

    when (result) {
      RESULT_DISABLE -> SettingsSyncSettings.getInstance().syncEnabled = false
      RESULT_REMOVE_DATA_AND_DISABLE -> disableAndRemoveData()
    }
    updateStatusInfo()
  }

  private fun disableAndRemoveData() {
    val remoteCommunicator = SettingsSyncMain.getInstance().getRemoteCommunicator()
    object : Task.Modal(null, message("disable.remove.data.title"), false) {

      override fun run(indicator: ProgressIndicator) {
        SettingsSyncSettings.getInstance().syncEnabled = false
        remoteCommunicator.delete()
      }

      override fun onThrowable(error: Throwable) {
        showError(statusLabel, message("disable.remove.data.failure"), error.localizedMessage)
      }
    }.queue()
  }

  private fun showError(component: JComponent, message: @Nls String, details: @Nls String) {
    val builder = JBPopupFactory.getInstance().createBalloonBuilder(JLabel(details))
    val balloon = builder.setTitle(message)
      .setFillColor(HintUtil.getErrorColor())
      .setDisposable(disposable!!)
      .createBalloon()
    balloon.showInCenterOf(component)
  }

  private fun updateStatusInfo() {
    if (::statusLabel.isInitialized) {
      val messageBuilder = StringBuilder()
      statusLabel.icon = null
      if (SettingsSyncSettings.getInstance().syncEnabled) {
        val statusTracker = SettingsSyncStatusTracker.getInstance()
        if (statusTracker.isSyncSuccessful()) {
          messageBuilder
            .append(message("sync.status.enabled"))
          if (statusTracker.isSynced()) {
            messageBuilder
              .append(' ')
              .append(message("sync.status.last.sync.message", getReadableSyncTime(), getUserName()))
          }
        }
        else {
          messageBuilder.append(message("sync.status.failed"))
          statusLabel.icon = AllIcons.General.Error
          messageBuilder.append(' ').append(statusTracker.getErrorMessage())
        }
      }
      else {
        messageBuilder.append(message("sync.status.disabled"))
      }
      @Suppress("HardCodedStringLiteral") // The above strings are localized
      statusLabel.text = messageBuilder.toString()
    }
  }

  private fun getReadableSyncTime(): String {
    return DateFormatUtil.formatPrettyDateTime(SettingsSyncStatusTracker.getInstance().getLastSyncTime()).lowercase()
  }

  private fun getUserName(): String {
    return SettingsSyncAuthService.getInstance().getUserData()?.loginName ?: "?"
  }

  override fun syncStatusChanged() {
    updateStatusInfo()
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    SettingsSyncStatusTracker.getInstance().removeListener(this)
  }
}

class SettingsSyncConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = SettingsSyncConfigurable()

  override fun canCreateConfigurable() = isSettingsSyncEnabledByKey()
}