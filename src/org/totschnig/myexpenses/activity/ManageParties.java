/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.activity;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.EditTextDialog;
import org.totschnig.myexpenses.dialog.EditTextDialog.EditTextDialogListener;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.fragment.TaskExecutionFragment;
import org.totschnig.myexpenses.model.Category;
import org.totschnig.myexpenses.model.Payee;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.ContextMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ManageParties extends ProtectedFragmentActivity implements
    EditTextDialogListener{
  Cursor mPartiesCursor;
  Button mDeleteButton;

  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeId());
    super.onCreate(savedInstanceState);
    setContentView(R.layout.manage_parties);
    setTitle(R.string.pref_manage_parties_title);
  }
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getSupportMenuInflater();
    inflater.inflate(R.menu.parties, menu);
    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean dispatchCommand(int command, Object tag) {
    if (command == R.id.CREATE_COMMAND) {
      Bundle args = new Bundle();
      args.putString("dialogTitle", getString(R.string.menu_create_party));
      EditTextDialog.newInstance(args).show(getSupportFragmentManager(), "CREATE_PARTY");
      return true;
    }
    return super.dispatchCommand(command, tag);
   }
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    menu.add(0, R.id.EDIT_COMMAND, 0, R.string.menu_edit_party);
    menu.add(0, R.id.DELETE_COMMAND, 0, R.string.menu_delete);
  }

  @Override
  public boolean onContextItemSelected(android.view.MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    switch(item.getItemId()) {
    case R.id.DELETE_COMMAND:
      FragmentManager fm = getSupportFragmentManager();
      fm.beginTransaction()
        .add(TaskExecutionFragment.newInstance(TaskExecutionFragment.TASK_DELETE_PAYEE,info.id, null), "DELETE_TASK")
        .commit();
      return true;
    case R.id.EDIT_COMMAND:
      Bundle args = new Bundle();
      args.putLong("partyId", info.id);
      args.putString("dialogTitle", getString(R.string.menu_edit_party));
      args.putString("value",((TextView) info.targetView.findViewById(android.R.id.text1)).getText().toString());
      EditTextDialog.newInstance(args).show(getSupportFragmentManager(), "EDIT_PARTY");
      return true;
    }
    return super.onContextItemSelected(item);
  }
  @Override
  public void onFinishEditDialog(Bundle args) {
    Long partyId;
    String value = args.getString("result");
    boolean success;
    if ((partyId = args.getLong("partyId")) != 0L)
      success = Payee.update(value,partyId) != -1;
    else
      success = Payee.create(value) != -1;
    if (!success)
      Toast.makeText(ManageParties.this,getString(R.string.already_defined, value), Toast.LENGTH_LONG).show();
  }
}
