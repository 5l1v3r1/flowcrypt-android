/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.flowcrypt.email.DoesNotNeedMailserver
import com.flowcrypt.email.R
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withEmptyRecyclerView
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.ui.activity.settings.AttesterSettingsActivity
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * @author Denis Bondarenko
 * Date: 23.02.2018
 * Time: 10:11
 * E-mail: DenBond7@gmail.com
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@DoesNotNeedMailserver
class AttesterSettingsActivityTest : BaseTest() {
  override val activityTestRule: ActivityTestRule<*>? = ActivityTestRule(AttesterSettingsActivity::class.java)

  @get:Rule
  var ruleChain: TestRule = RuleChain
      .outerRule(ClearAppSettingsRule())
      .around(AddAccountToDatabaseRule())
      .around(activityTestRule)

  @Before
  fun registerIdling() {
    val activity = activityTestRule?.activity ?: return
    if (activity is AttesterSettingsActivity) {
      IdlingRegistry.getInstance().register(activity.idlingForAttester)
    }
  }

  @After
  fun unregisterIdling() {
    val activity = activityTestRule?.activity ?: return
    if (activity is AttesterSettingsActivity) {
      IdlingRegistry.getInstance().unregister(activity.idlingForAttester)
    }
  }

  @Test
  fun testKeysExistOnAttester() {
    onView(withId(R.id.rVAttester))
        .check(matches(not<View>(withEmptyRecyclerView()))).check(matches(isDisplayed()))
    onView(withId(R.id.empty))
        .check(matches(not<View>(isDisplayed())))
  }
}
