/* Business Source License 1.0 © 2016 Tom James Holub (human@flowcrypt.com). Use limitations apply. This version will change to GPLv3 on 2021-01-01. See https://github.com/tomholub/cryptup-chrome/tree/master/src/LICENCE */

'use strict';

var openpgp = window.openpgp;
var catcher = window.catcher;
var btoa = window.btoa;
var atob = window.atob;
var MimeParser = this['emailjs-mime-parser'];
var MimeBuilder = this['emailjs-mime-builder'];