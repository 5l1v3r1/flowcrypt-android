/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.base

import android.app.Activity
import android.view.MenuItem
import com.flowcrypt.email.ui.activity.BaseNodeActivity

/**
 * The base back stack activity. In this activity we add the back stack functionality. The
 * extended class must implement [BaseBackStackActivity.getContentViewResourceId] method
 * to define the content view resources id. And the in [Activity.onCreate] method
 * we setup the toolbar if it exist in the contents and call
 * [androidx.appcompat.app.ActionBar.setDisplayHomeAsUpEnabled] to implement the
 * back stack functionality.
 *
 * @author DenBond7
 * Date: 26.05.2017
 * Time: 10:03
 * E-mail: DenBond7@gmail.com
 */

abstract class BaseBackStackActivity : BaseNodeActivity() {

  override val isDisplayHomeAsUpEnabled: Boolean
    get() = true

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        onBackPressed()
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }
}
