package org.totschnig.myexpenses.activity;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.VersionDialogFragment;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.provider.filter.Criteria;
import org.totschnig.myexpenses.util.ContribUtils;
import org.totschnig.myexpenses.util.DistribHelper;
import org.totschnig.myexpenses.util.PermissionHelper;
import org.totschnig.myexpenses.util.Utils;

import java.io.File;
import java.util.Map;

import timber.log.Timber;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNTID;

public abstract class LaunchActivity extends ProtectedFragmentActivity {

  /**
   * check if this is the first invocation of a new version
   * in which case help dialog is presented
   * also is used for hooking version specific upgrade procedures
   * and display information to be presented upon app launch
   */
  public void newVersionCheck() {
    int prev_version = PrefKey.CURRENT_VERSION.getInt(-1);
    int current_version = DistribHelper.getVersionNumber();
    boolean showImportantUpgradeInfo = false;
    if (prev_version < current_version) {
      if (prev_version == -1) {
        return;
      }
      PrefKey.CURRENT_VERSION.putInt(current_version);
      SharedPreferences settings = MyApplication.getInstance().getSettings();
      Editor edit = settings.edit();
      if (prev_version < 19) {
        edit.putString(PrefKey.SHARE_TARGET.getKey(), settings.getString("ftp_target", ""));
        edit.remove("ftp_target");
        edit.apply();
      }
      if (prev_version < 28) {
        Timber.i("Upgrading to version 28: Purging %d transactions from datbase",
            getContentResolver().delete(TransactionProvider.TRANSACTIONS_URI,
                KEY_ACCOUNTID + " not in (SELECT _id FROM accounts)", null));
      }
      if (prev_version < 30) {
        if (!"".equals(PrefKey.SHARE_TARGET.getString(""))) {
          edit.putBoolean(PrefKey.SHARE_TARGET.getKey(), true);
          edit.apply();
        }
      }
      if (prev_version < 40) {
        //this no longer works since we migrated time to utc format
        //  DbUtils.fixDateValues(getContentResolver());
        //we do not want to show both reminder dialogs too quickly one after the other for upgrading users
        //if they are already above both tresholds, so we set some delay
        edit.putLong("nextReminderContrib", Transaction.getSequenceCount() + 23);
        edit.apply();
      }
      if (prev_version < 163) {
        edit.remove("qif_export_file_encoding");
        edit.apply();
      }
      if (prev_version < 199) {
        //filter serialization format has changed
        for (Map.Entry<String, ?> entry : settings.getAll().entrySet()) {
          String key = entry.getKey();
          String[] keyParts = key.split("_");
          if (keyParts[0].equals("filter")) {
            String val = settings.getString(key, "");
            switch (keyParts[1]) {
              case "method":
              case "payee":
              case "cat":
                int sepIndex = val.indexOf(";");
                edit.putString(key, val.substring(sepIndex + 1) + ";" + Criteria.escapeSeparator(val.substring(0, sepIndex)));
                break;
              case "cr":
                edit.putString(key, Transaction.CrStatus.values()[Integer.parseInt(val)].name());
                break;
            }
          }
        }
        edit.apply();
      }
      if (prev_version < 202) {
        String appDir = PrefKey.APP_DIR.getString(null);
        if (appDir != null) {
          PrefKey.APP_DIR.putString(Uri.fromFile(new File(appDir)).toString());
        }
      }
      if (prev_version < 221) {
        PrefKey.SORT_ORDER_LEGACY.putString(
            PrefKey.CATEGORIES_SORT_BY_USAGES_LEGACY.getBoolean(true) ?
                "USAGES" : "ALPHABETIC");
      }
      VersionDialogFragment.newInstance(prev_version, showImportantUpgradeInfo)
          .show(getSupportFragmentManager(), "VERSION_INFO");
    } else {
      if (MyApplication.getInstance().getLicenceHandler().hasLegacyLicence() &&
          !PrefKey.LICENCE_MIGRATION_INFO_SHOWN.getBoolean(false)) {
        Bundle bundle = new Bundle();
        bundle.putCharSequence(
            ConfirmationDialogFragment.KEY_MESSAGE,
            Utils.getTextWithAppName(this, R.string.licence_migration_info));
        bundle.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
            R.id.REQUEST_LICENCE_MIGRATION_COMMAND);
        bundle.putString(ConfirmationDialogFragment.KEY_PREFKEY, PrefKey
            .LICENCE_MIGRATION_INFO_SHOWN.getKey());
        bundle.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string.pref_request_licence_title);
        ConfirmationDialogFragment.newInstance(bundle).show(getSupportFragmentManager(),
            "RESTORE");
      }
      if (!ContribFeature.SYNCHRONIZATION.hasAccess() && ContribFeature.SYNCHRONIZATION.usagesLeft() < 1 &&
          !PrefKey.SYNC_UPSELL_NOTIFICATION_SHOWN.getBoolean(false)) {
        PrefKey.SYNC_UPSELL_NOTIFICATION_SHOWN.putBoolean(true);
        ContribUtils.showContribNotification(this, ContribFeature.SYNCHRONIZATION);
      }
    }
    checkCalendarPermission();
  }

  private void checkCalendarPermission() {
    if (!PrefKey.PLANNER_CALENDAR_ID.getString("-1").equals("-1")) {
      if (ContextCompat.checkSelfPermission(this,
          Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_DENIED) {
        requestPermission(PermissionHelper.PermissionGroup.CALENDAR);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case PermissionHelper.PERMISSIONS_REQUEST_WRITE_CALENDAR:
        if (grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_DENIED) {
          if (!ActivityCompat.shouldShowRequestPermissionRationale(
              this, Manifest.permission.WRITE_CALENDAR)) {
            MyApplication.getInstance().removePlanner();
          }
        }
        break;
    }
  }
}
