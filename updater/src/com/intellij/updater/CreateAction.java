package com.intellij.updater;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CreateAction extends PatchAction {
  public CreateAction(String path) {
    super(path, -1);
  }

  public CreateAction(DataInputStream in) throws IOException {
    super(in);
  }

  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    Runner.logger.info("building PatchFile");
    patchOutput.putNextEntry(new ZipEntry(myPath));

    writeExecutableFlag(patchOutput, newerFile);
    Utils.copyFileToStream(newerFile, patchOutput);

    patchOutput.closeEntry();
  }

  @Override
  protected ValidationResult doValidate(File toFile) {
    Runner.logger.info("validation the result");
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.CREATE);
    if (result != null) return result;

    if (toFile.exists()) {
      return new ValidationResult(ValidationResult.Kind.CONFLICT,
                                  myPath,
                                  ValidationResult.Action.CREATE,
                                  ValidationResult.ALREADY_EXISTS_MESSAGE,
                                  ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP);
    }
    return null;
  }

  @Override
  protected void doApply(ZipFile patchFile, File toFile) throws IOException {
    prepareToWriteFile(toFile);

    InputStream in = Utils.getEntryInputStream(patchFile, myPath);
    try {
      boolean executable = readExecutableFlag(in);
      Utils.copyStreamToFile(in, toFile);
      Runner.logger.info("copyStreamToFile to file: " + toFile.getCanonicalPath());
      Utils.setExecutable(toFile, executable);
      Runner.logger.info("setExecutable for file: " + toFile.getCanonicalPath());
    }
    catch (Exception ex) {
      Runner.logger.error(ex.fillInStackTrace());
    }
    finally {
      in.close();
    }
  }

  private static void prepareToWriteFile(File file) throws IOException {
    if (file.exists()) {
      Utils.delete(file);
      Runner.logger.info("deleted File: " + file.getCanonicalPath());
      return;
    }

    while (file != null && !file.exists()) {
      file = file.getParentFile();
    }
    if (file != null && !file.isDirectory()) {
      Utils.delete(file);
      Runner.logger.info("deleted File(parent): " + file.getCanonicalPath());
    }
  }

  protected void doBackup(File toFile, File backupFile) {
    // do nothing
  }

  protected void doRevert(File toFile, File backupFile) throws IOException {
    Utils.delete(toFile);
    Runner.logger.info("deleted file: " + toFile.getCanonicalPath());
  }
}
