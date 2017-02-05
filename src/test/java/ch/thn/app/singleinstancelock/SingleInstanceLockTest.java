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

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import ch.thn.app.singleinstancelock.SingleInstanceLock.SingleInstanceLockError;

/**
 *
 * @author Thomas Naeff (github.com/thnaeff)
 *
 */
public class SingleInstanceLockTest {

  @Rule
  public final ExpectedException exception = ExpectedException.none();


  @Test
  public void testSingleApplication() throws Exception {

    SingleInstanceLock.setApplicationId(getClass().getSimpleName());

    boolean locked = SingleInstanceLock.lock();

    assertTrue(locked);

    boolean thisApplicationHasLock = SingleInstanceLock.lock();

    assertTrue(thisApplicationHasLock);

    File lockFile = SingleInstanceLock.getLockFile();

    assertTrue(lockFile.exists());

    SingleInstanceLock.release();

    // Check that lock file got removed
    assertTrue(!lockFile.exists());


    // Lock again
    locked = SingleInstanceLock.lock();

    assertTrue(locked);

    // Release again
    SingleInstanceLock.release();

    // Check that lock file got removed
    assertTrue(!lockFile.exists());

  }


  @Test
  public void testSetIdAgain() throws Exception {

    SingleInstanceLock.setApplicationId(getClass().getSimpleName());

    boolean locked = SingleInstanceLock.lock();

    assertTrue(locked);

    // The application is locked already. Can not set an ID now
    try {
      exception.expect(SingleInstanceLockError.class);
      SingleInstanceLock.setApplicationId("SomeOtherID");
    } finally {
      // Make sure lock is released so that other test can be run

      File lockFile = SingleInstanceLock.getLockFile();

      SingleInstanceLock.release();

      // Check that lock file got removed
      assertTrue(!lockFile.exists());
    }

  }


  @Test
  public void testSetDirWhenLocked() throws Exception {

    SingleInstanceLock.setApplicationId(getClass().getSimpleName());

    boolean locked = SingleInstanceLock.lock();

    assertTrue(locked);

    try {
      // The application is locked already. Can not set an ID now
      exception.expect(SingleInstanceLockError.class);
      // Set to current directory
      SingleInstanceLock.setLockFileDirectory("");
    } finally {
      // Make sure lock is released so that other test can be run

      File lockFile = SingleInstanceLock.getLockFile();

      SingleInstanceLock.release();

      // Check that lock file got removed
      assertTrue(!lockFile.exists());
    }

  }


}
