package com.example

import com.example.update.AppVersionComparator
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun appVersionComparator_stripsPrefixAndComparesSemver() {
    assertTrue(AppVersionComparator.isRemoteNewer("v1.0.8", "1.0.7"))
    assertTrue(AppVersionComparator.isRemoteNewer("v1.1.0", "1.0.99"))
    assertTrue(AppVersionComparator.isRemoteNewer("V2.0.0", "1.9.9"))
    assertFalse(AppVersionComparator.isRemoteNewer("v1.0.7", "1.0.7"))
    assertFalse(AppVersionComparator.isRemoteNewer("v1.0.6", "1.0.7"))
    assertEquals("1.2.3", AppVersionComparator.normalize("v1.2.3-beta+5"))
  }
}
