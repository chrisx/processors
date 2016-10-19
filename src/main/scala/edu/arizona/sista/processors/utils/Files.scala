package edu.arizona.sista.processors.utils

import java.io.File

/**
 * File utilities
 * User: mihais
 * Date: 1/9/14
 */
object Files {
  //val TEMP_DIR_ATTEMPTS = 1

  def mkTmpDir(prefix: String, deleteOnExit: Boolean): String = {
    val baseDir = new File(System.getProperty("java.io.tmpdir"))

    // to minimize collisions, the dir name contains the time and the thread id
    //val baseName = prefix + "-" + System.nanoTime().toString + "-" + Thread.currentThread().getId + "-"

    val baseName = prefix + "-" + Thread.currentThread().getId + "-"

    //    for (counter <- 0 until TEMP_DIR_ATTEMPTS) {
    //      val tempDir = new File(baseDir, baseName + counter.toString)
    //      if (tempDir.mkdir()) {
    //        if (deleteOnExit) tempDir.deleteOnExit()
    //        return tempDir.getAbsolutePath
    //      } else tempDir.getAbsolutePath
    //    }

    val tempDir = new File(baseDir, baseName)
    if (tempDir.mkdir()) {
      if (deleteOnExit) tempDir.deleteOnExit()
      return tempDir.getAbsolutePath
    } else tempDir.getAbsolutePath

    //    throw new IllegalStateException("ERROR: Failed to create directory within "
    //      + TEMP_DIR_ATTEMPTS + " attempts (tried "
    //      + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
  }
}
