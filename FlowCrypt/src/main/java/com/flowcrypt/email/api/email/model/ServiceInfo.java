/*
 * © 2016-2018 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * This class describes service info details. Can be used when create a new messages.
 *
 * @author Denis Bondarenko
 * Date: 24.11.2017
 * Time: 10:57
 * E-mail: DenBond7@gmail.com
 */

public class ServiceInfo implements Parcelable {
  public static final Creator<ServiceInfo> CREATOR = new Creator<ServiceInfo>() {
    @Override
    public ServiceInfo createFromParcel(Parcel source) {
      return new ServiceInfo(source);
    }

    @Override
    public ServiceInfo[] newArray(int size) {
      return new ServiceInfo[size];
    }
  };
  private boolean isToFieldEditable;
  private boolean isFromFieldEditable;
  private boolean isMsgEditable;
  private boolean isSubjEditable;
  private boolean isMsgTypeSwitchable;
  private boolean hasAbilityToAddNewAtt;
  private String systemMsg;
  private List<AttachmentInfo> atts;

  public ServiceInfo(boolean isToFieldEditable, boolean isFromFieldEditable, boolean isMessageEditEnable,
                     boolean isSubjectEditEnable, boolean isMsgTypeSwitchable, boolean isAddNewAttachmentsEnable,
                     String systemMsg, List<AttachmentInfo> atts) {
    this.isToFieldEditable = isToFieldEditable;
    this.isFromFieldEditable = isFromFieldEditable;
    this.isMsgEditable = isMessageEditEnable;
    this.isSubjEditable = isSubjectEditEnable;
    this.isMsgTypeSwitchable = isMsgTypeSwitchable;
    this.hasAbilityToAddNewAtt = isAddNewAttachmentsEnable;
    this.systemMsg = systemMsg;
    this.atts = atts;
  }

  protected ServiceInfo(Parcel in) {
    this.isToFieldEditable = in.readByte() != 0;
    this.isFromFieldEditable = in.readByte() != 0;
    this.isMsgEditable = in.readByte() != 0;
    this.isSubjEditable = in.readByte() != 0;
    this.isMsgTypeSwitchable = in.readByte() != 0;
    this.hasAbilityToAddNewAtt = in.readByte() != 0;
    this.systemMsg = in.readString();
    this.atts = in.createTypedArrayList(AttachmentInfo.CREATOR);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeByte(this.isToFieldEditable ? (byte) 1 : (byte) 0);
    dest.writeByte(this.isFromFieldEditable ? (byte) 1 : (byte) 0);
    dest.writeByte(this.isMsgEditable ? (byte) 1 : (byte) 0);
    dest.writeByte(this.isSubjEditable ? (byte) 1 : (byte) 0);
    dest.writeByte(this.isMsgTypeSwitchable ? (byte) 1 : (byte) 0);
    dest.writeByte(this.hasAbilityToAddNewAtt ? (byte) 1 : (byte) 0);
    dest.writeString(this.systemMsg);
    dest.writeTypedList(this.atts);
  }

  public boolean isToFieldEditable() {
    return isToFieldEditable;
  }

  public boolean isFromFieldEditable() {
    return isFromFieldEditable;
  }

  public boolean isMessageEditable() {
    return isMsgEditable;
  }

  public boolean isMessageTypeSwitchable() {
    return isMsgTypeSwitchable;
  }

  public String getSystemMessage() {
    return systemMsg;
  }

  public List<AttachmentInfo> getAttachments() {
    return atts;
  }

  public boolean isSubjectEditable() {
    return isSubjEditable;
  }

  public boolean hasAbilityToAddNewAtt() {
    return hasAbilityToAddNewAtt;
  }

  public static class Builder {
    private boolean isToFieldEditable = true;
    private boolean isFromFieldEditable = true;
    private boolean isMsgEditable = true;
    private boolean isSubjEditable = true;
    private boolean isMsgTypeSwitchable = true;
    private boolean hasAbilityToAddNewAtt = true;
    private String systemMessage;
    private List<AttachmentInfo> atts;

    public Builder setIsToFieldEditable(boolean isToFieldEditable) {
      this.isToFieldEditable = isToFieldEditable;
      return this;
    }

    public Builder setIsFromFieldEditable(boolean isFromFieldEditable) {
      this.isFromFieldEditable = isFromFieldEditable;
      return this;
    }

    public Builder setIsMessageEditable(boolean isMsgEditable) {
      this.isMsgEditable = isMsgEditable;
      return this;
    }

    public Builder setIsSubjectEditable(boolean isSubjEditable) {
      this.isSubjEditable = isSubjEditable;
      return this;
    }

    public Builder setIsMessageTypeSwitchable(boolean isMsgTypeSwitchable) {
      this.isMsgTypeSwitchable = isMsgTypeSwitchable;
      return this;
    }

    public Builder setHasAbilityToAddNewAttachment(boolean hasAbilityToAddNewAtt) {
      this.hasAbilityToAddNewAtt = hasAbilityToAddNewAtt;
      return this;
    }

    public Builder setSystemMessage(String systemMessage) {
      this.systemMessage = systemMessage;
      return this;
    }

    public Builder setAttachments(List<AttachmentInfo> attachments) {
      this.atts = attachments;
      return this;
    }

    public ServiceInfo build() {
      return new ServiceInfo(isToFieldEditable, isFromFieldEditable, isMsgEditable, isSubjEditable,
          isMsgTypeSwitchable, hasAbilityToAddNewAtt, systemMessage, atts);
    }
  }
}
