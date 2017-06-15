/*
 * Business Source License 1.0 © 2017 FlowCrypt Limited (tom@cryptup.org). Use limitations apply.
 * See https://github.com/FlowCrypt/flowcrypt-android/blob/master/LICENSE
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.Date;

/**
 * Simple POJO class which describe a general message details.
 *
 * @author DenBond7
 *         Date: 28.04.2017
 *         Time: 11:51
 *         E-mail: DenBond7@gmail.com
 */

public class GeneralMessageDetails implements Parcelable, Comparable<GeneralMessageDetails> {

    public static final Creator<GeneralMessageDetails> CREATOR = new
            Creator<GeneralMessageDetails>() {
                @Override
                public GeneralMessageDetails createFromParcel(Parcel source) {
                    return new GeneralMessageDetails(source);
                }

                @Override
                public GeneralMessageDetails[] newArray(int size) {
                    return new GeneralMessageDetails[size];
                }
            };

    private String from;
    private String subject;
    private Date receiveDate;
    private long uid;
    private boolean isSeen;

    public GeneralMessageDetails(String from, String subject, Date receiveDate, long uid, boolean
            isSeen) {
        this.from = from;
        this.subject = subject;
        this.receiveDate = receiveDate;
        this.uid = uid;
        this.isSeen = isSeen;
    }

    public GeneralMessageDetails() {
    }

    protected GeneralMessageDetails(Parcel in) {
        this.from = in.readString();
        this.subject = in.readString();
        long tmpReceiveDate = in.readLong();
        this.receiveDate = tmpReceiveDate == -1 ? null : new Date(tmpReceiveDate);
        this.uid = in.readLong();
        this.isSeen = in.readByte() != 0;
    }

    @Override
    public int compareTo(@NonNull GeneralMessageDetails o) {
        Date dateToCompare = o.getReceiveDate();

        if (receiveDate == null && dateToCompare == null) {
            return 0;
        } else if (receiveDate == null) {
            return -1;
        } else if (dateToCompare == null) {
            return 0;
        }

        return receiveDate.compareTo(dateToCompare);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.from);
        dest.writeString(this.subject);
        dest.writeLong(this.receiveDate != null ? this.receiveDate.getTime() : -1);
        dest.writeLong(this.uid);
        dest.writeByte(this.isSeen ? (byte) 1 : (byte) 0);
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Date getReceiveDate() {
        return receiveDate;
    }

    public void setReceiveDate(Date receiveDate) {
        this.receiveDate = receiveDate;
    }

    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public boolean isSeen() {
        return isSeen;
    }

    public void setSeen(boolean seen) {
        isSeen = seen;
    }
}
