/**
 * Copyright 2014 Thomas Naeff (github.com/thnaeff)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package ch.thn.app.singleinstancelock;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import ch.thn.file.fileutil.FileUtil;

/**
 * To have only a single instance of a program running, some cross application "communication" is
 * necessary. This class implements a locking mechanism based on a lock file. This implementation
 * creates a file and tries to get an exclusive lock on the file. If the locking is successful, the
 * application can start. If the locking is not successful that means that some other application
 * already acquired the lock and therefore the new application can not start.<br />
 * <br />
 * This implementation takes care of creating and deleting the lock file. Also, since it is not the
 * presence of the file but the lock on the file which determines the lock, this implementation
 * works even the application crashes without being able to delete the lock file. <br />
 * The lock file is created by default in the systems temporary directory. A application ID has to
 * be set before this class can be used (see {@link #setApplicationId(String)}).<br />
 * <br />
 * <b>Note:</b> This class is intended to be used only once with a single call to lock (when the
 * application starts) and if lock() returns <code>false</code> the application should be
 * terminated. It is possible though to call lock() repeatedly to check if it is still locked and if
 * the lock file still exists (throws an exception if the lock file disappears). <br />
 * <br />
 * <br />
 * Implemented as singleton. Better enum based approach instead of singleton?
 * http://stackoverflow.com/questions/70689/what-is-an-efficient-way-to-implement-a-singleton-pattern-in-java
 *
 *
 *
 * @author Thomas Naeff (github.com/thnaeff)
 *
 */
public class SingleInstanceLock {

  /**
   * The lock file extension
   */
  private static final String LOCKFILE_EXT = ".lock";

  private static String applicationId = null;

  private static RandomAccessFile randomAccessFile = null;
  private static FileChannel fileChannel = null;
  private static FileLock fileLock = null;

  private static File file = null;

  /**
   * A unique ID for this application. The ID will be used to identify the lock file.
   *
   * @param applicationId The unique application ID
   * @throws SingleInstanceLockError Thrown if a lock is already in place. Release the lock first to
   *         set a new ID
   */
  public static void setApplicationId(String applicationId) {

    if (file != null) {
      throw new SingleInstanceLockError(
          "Lock is already in place. Release lock before setting a new ID.");
    }

    SingleInstanceLock.applicationId = applicationId;

    // Have the file created again
    file = null;
  }

  /**
   * Sets the directory in which the lock file should be created. If set to <code>null</code>, the
   * lock file is created in the systems temporary directory. If set to an empty string, the lock
   * file is created in the current working directory.
   *
   * @param lockFileDir
   */
  public static void setLockFileDirectory(String lockFileDir) {

    if (file != null) {
      throw new SingleInstanceLockError(
          "Lock is already in place. Release lock before setting a new ID.");
    }

    if (lockFileDir == null) {
      // Create a file in the system temporary directory
      file = new File(FileUtil.addPathSeparator(System.getProperty("java.io.tmpdir"))
          + applicationId + LOCKFILE_EXT);
    } else if (lockFileDir.length() == 0) {
      file = new File(applicationId + LOCKFILE_EXT);
    } else {
      file = new File(FileUtil.addPathSeparator(lockFileDir) + applicationId + LOCKFILE_EXT);
    }

    // Even though the javdoc says "Deletion will be attempted only for normal
    // termination of the virtual machine..." it seems like terminating the running
    // application in eclipse also results in the file being removed.
    file.deleteOnExit();
  }

  /**
   * Returns the file which is used as lock file. Is only available after the first call to
   * {@link #lock()}.
   *
   *
   * @return The lock file
   */
  public static File getLockFile() {
    return file;
  }

  /**
   * Create the lock for this application. If the lock already exists it returns <code>false</code>
   * which means an application instance is already running and has acquired the lock. If no lock
   * exists, the lock is created and <code>true</code> is returned.
   *
   * @return <code>true</code> if no lock existed and locking was successful, <code>false</code> if
   *         a lock already existed which means another application is already running.
   * @throws SingleInstanceLockError If the lock can not be acquired for some other reason than if
   *         another application has the lock already. Reasons could be that the file can not be
   *         created, the application ID is not set, etc.
   */
  public static boolean lock() throws SingleInstanceLockError {

    if (applicationId == null) {
      throw new SingleInstanceLockError("Application ID not set");
    }

    // Make sure a file object is created
    if (file == null) {
      setLockFileDirectory(null);
    }

    if (fileChannel == null || !fileChannel.isOpen() || randomAccessFile == null) {

      try {
        randomAccessFile = new RandomAccessFile(file, "rw");
        fileChannel = randomAccessFile.getChannel();
      } catch (FileNotFoundException e) {
        throw new SingleInstanceLockError("Failed to access/create lock file " + file.getPath(), e);
      }

      if (fileChannel == null) {
        throw new SingleInstanceLockError("Failed to open lock file channel");
      }
    } else {
      // File channel has already been opened previously. Does the file still exist?
      if (!file.exists()) {
        // Something deleted the file -> start fresh
        try {
          release(true);
        } catch (IOException e) {
          // Try to release and delete, thats all we can do at this point. If an IOException
          // occurred we will notice the next time the lock is called if all is ok.
        }
        throw new SingleInstanceLockError("Lock file " + file.getPath() + " disappeared");
      }

    }

    if (fileLock == null) {
      try {
        fileLock = fileChannel.tryLock();
      } catch (ClosedChannelException e) {
        try {
          release(false);
        } catch (IOException e1) {
          // Trying our best to clean up
        }
        throw new SingleInstanceLockError("File locking failed. Channel is closed", e);
      } catch (IOException e) {
        try {
          release(false);
        } catch (IOException e1) {
          // Trying our best to clean up
        }
        throw new SingleInstanceLockError("File locking failed", e);
      } catch (OverlappingFileLockException e) {
        try {
          release(false);
        } catch (IOException e1) {
          // Trying our best to clean up
        }
        return false;
      }


      // Was locking not successful?
      if (fileLock == null) {
        // Could not get the lock. Must be locked already from some other application
        return false;
      }

      // Has lock

      // Add shutdown hook to call release on shutdown
      Runtime.getRuntime().addShutdownHook(new SingleInstanceLockShutdownHook());

      return true;
    } else {
      if (fileLock.isValid()) {
        // Still has lock
        return true;
      }

      return false;
    }


  }

  /**
   * Releases the lock.
   *
   * @throws IOException When something went wrong when releasing the lock. The lock might still be
   *         released successfully, but it is not guaranteed.
   */
  public static void release() throws IOException {
    release(true);
  }

  /**
   * Releases the lock.
   *
   *
   * @param deleteFile Whether or not the lock file should be deleted
   * @throws IOException When something went wrong when releasing the lock. The lock might still be
   *         released successfully, but it is not guaranteed.
   */
  private static void release(boolean deleteFile) throws IOException {

    IOException ioexception = null;

    try {
      if (fileLock != null && fileLock.isValid()) {
        fileLock.release();
      }
    } catch (IOException e) {
      ioexception = e;
    } finally {
      fileLock = null;
    }

    try {
      if (fileChannel != null && fileChannel.isOpen()) {
        fileChannel.close();
      }
    } catch (IOException e) {
      ioexception = e;
    } finally {
      fileChannel = null;
    }

    try {
      if (randomAccessFile != null) {
        randomAccessFile.close();
      }

      randomAccessFile = null;
    } catch (IOException e) {
      ioexception = e;
    }

    if (deleteFile) {
      if (file != null) {
        file.delete();
      }
    }

    file = null;

    if (ioexception != null) {
      throw new IOException(
          "A problem ocurred when releasing the lock. The lock might or migth not be released successfully",
          ioexception);
    }

  }


  /***************************************************************************
   *
   * Shutdown hook which is called when application ends to release the lock.
   *
   * @author Thomas Naeff (github.com/thnaeff)
   *
   */
  private static class SingleInstanceLockShutdownHook extends Thread {

    /**
     *
     */
    public SingleInstanceLockShutdownHook() {
      setName(SingleInstanceLockShutdownHook.class.getName());
    }


    @Override
    public void run() {
      try {
        release(true);
      } catch (IOException e) {
        // Application is shutting down. Nothing more to do.
      }
    }

  }


  /***************************************************************************
   *
   *
   * @author Thomas Naeff (github.com/thnaeff)
   *
   */
  public static class SingleInstanceLockError extends Error {
    private static final long serialVersionUID = -6879290461614049645L;


    /**
     *
     *
     * @param msg
     */
    public SingleInstanceLockError(String msg) {
      super(msg);
    }


    /**
     *
     *
     * @param msg
     * @param e
     */
    public SingleInstanceLockError(String msg, Throwable e) {
      super(msg, e);
    }


  }
}
