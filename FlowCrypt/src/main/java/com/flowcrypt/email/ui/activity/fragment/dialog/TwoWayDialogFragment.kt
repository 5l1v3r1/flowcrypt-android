/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.flowcrypt.email.R
import com.flowcrypt.email.util.GeneralUtil

/**
 * This dialog can be used if we need to show a simple info dialog which has two buttons (negative and positive).
 *
 * @author Denis Bondarenko
 * Date: 28.08.2018
 * Time: 15:28
 * E-mail: DenBond7@gmail.com
 */
class TwoWayDialogFragment : DialogFragment() {

  private var dialogTitle: String? = null
  private var dialogMsg: String? = null
  private var positiveBtnTitle: String? = null
  private var negativeBtnTitle: String? = null
  private var listener: OnTwoWayDialogListener? = null
  private var requestCode: Int = 0

  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (context is OnTwoWayDialogListener) {
      this.listener = context
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    dialogTitle = arguments?.getString(KEY_DIALOG_TITLE, getString(R.string.info))
    dialogMsg = arguments?.getString(KEY_DIALOG_MESSAGE)
    positiveBtnTitle = arguments?.getString(KEY_POSITIVE_BUTTON_TITLE, getString(R.string.yes))
    negativeBtnTitle = arguments?.getString(KEY_NEGATIVE_BUTTON_TITLE, getString(R.string.no))
    isCancelable = arguments?.getBoolean(KEY_IS_CANCELABLE, false) ?: false
    requestCode = arguments?.getInt(KEY_REQUEST_CODE, 0) ?: 0
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialogBuilder = AlertDialog.Builder(requireContext())

    dialogBuilder.setTitle(dialogTitle)
    dialogBuilder.setMessage(dialogMsg)

    dialogBuilder.setPositiveButton(positiveBtnTitle
    ) { _, _ ->
      sendResult(RESULT_OK)
      listener?.onDialogButtonClick(requestCode, RESULT_OK)
    }

    dialogBuilder.setNegativeButton(negativeBtnTitle
    ) { _, _ ->
      sendResult(RESULT_CANCELED)
      listener?.onDialogButtonClick(requestCode, RESULT_CANCELED)
    }

    return dialogBuilder.create()
  }

  private fun sendResult(result: Int) {
    if (targetFragment == null) {
      return
    }
    targetFragment?.onActivityResult(targetRequestCode, result, null)
  }

  /**
   * This interface can be used by an activity to receive results from the dialog when a user choose some button.
   */
  interface OnTwoWayDialogListener {
    /**
     * @param result Can be [RESULT_OK] if the user clicks the positive button, or
     * [RESULT_CANCELED] if the user clicks the negative button.
     */
    fun onDialogButtonClick(requestCode: Int, result: Int)
  }

  companion object {
    /** Standard activity result: operation canceled.  */
    const val RESULT_CANCELED = 0
    /** Standard activity result: operation succeeded. */
    const val RESULT_OK = 1

    private val KEY_REQUEST_CODE =
        GeneralUtil.generateUniqueExtraKey("KEY_REQUEST_CODE", TwoWayDialogFragment::class.java)
    private val KEY_DIALOG_TITLE =
        GeneralUtil.generateUniqueExtraKey("KEY_DIALOG_TITLE", TwoWayDialogFragment::class.java)
    private val KEY_DIALOG_MESSAGE =
        GeneralUtil.generateUniqueExtraKey("KEY_DIALOG_MESSAGE", TwoWayDialogFragment::class.java)
    private val KEY_POSITIVE_BUTTON_TITLE =
        GeneralUtil.generateUniqueExtraKey("KEY_POSITIVE_BUTTON_TITLE", TwoWayDialogFragment::class.java)
    private val KEY_NEGATIVE_BUTTON_TITLE =
        GeneralUtil.generateUniqueExtraKey("KEY_NEGATIVE_BUTTON_TITLE", TwoWayDialogFragment::class.java)
    private val KEY_IS_CANCELABLE =
        GeneralUtil.generateUniqueExtraKey("KEY_IS_CANCELABLE", TwoWayDialogFragment::class.java)

    @JvmStatic
    fun newInstance(requestCode: Int = 0, dialogTitle: String? = null,
                    dialogMsg: String? = null,
                    positiveButtonTitle: String? = null,
                    negativeButtonTitle: String? = null,
                    isCancelable: Boolean = true): TwoWayDialogFragment {
      val args = Bundle()
      args.putInt(KEY_REQUEST_CODE, requestCode)
      args.putString(KEY_DIALOG_TITLE, dialogTitle)
      args.putString(KEY_DIALOG_TITLE, dialogTitle)
      args.putString(KEY_DIALOG_MESSAGE, dialogMsg)
      args.putString(KEY_POSITIVE_BUTTON_TITLE, positiveButtonTitle)
      args.putString(KEY_NEGATIVE_BUTTON_TITLE, negativeButtonTitle)
      args.putBoolean(KEY_IS_CANCELABLE, isCancelable)
      val infoDialogFragment = TwoWayDialogFragment()
      infoDialogFragment.arguments = args

      return infoDialogFragment
    }
  }
}
