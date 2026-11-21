package com.kairos.app.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class KDao_Impl implements KDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Item> __insertionAdapterOfItem;

  private final EntityInsertionAdapter<Memo> __insertionAdapterOfMemo;

  private final EntityDeletionOrUpdateAdapter<Item> __deletionAdapterOfItem;

  private final EntityDeletionOrUpdateAdapter<Item> __updateAdapterOfItem;

  private final SharedSQLiteStatement __preparedStmtOfClearMemos;

  public KDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfItem = new EntityInsertionAdapter<Item>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `Item` (`id`,`kind`,`title`,`time`,`done`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Item entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getKind());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getTime());
        final int _tmp = entity.getDone() ? 1 : 0;
        statement.bindLong(5, _tmp);
      }
    };
    this.__insertionAdapterOfMemo = new EntityInsertionAdapter<Memo>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `Memo` (`id`,`role`,`text`,`ts`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Memo entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getRole());
        statement.bindString(3, entity.getText());
        statement.bindLong(4, entity.getTs());
      }
    };
    this.__deletionAdapterOfItem = new EntityDeletionOrUpdateAdapter<Item>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `Item` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Item entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfItem = new EntityDeletionOrUpdateAdapter<Item>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `Item` SET `id` = ?,`kind` = ?,`title` = ?,`time` = ?,`done` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Item entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getKind());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getTime());
        final int _tmp = entity.getDone() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindLong(6, entity.getId());
      }
    };
    this.__preparedStmtOfClearMemos = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM Memo";
        return _query;
      }
    };
  }

  @Override
  public Object add(final Item i, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfItem.insertAndReturnId(i);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object addMemo(final Memo m, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMemo.insert(m);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object del(final Item i, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfItem.handle(i);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upd(final Item i, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfItem.handle(i);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearMemos(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearMemos.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearMemos.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Item>> items() {
    final String _sql = "SELECT * FROM Item ORDER BY CASE WHEN time=0 THEN 1 ELSE 0 END, time";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"Item"}, new Callable<List<Item>>() {
      @Override
      @NonNull
      public List<Item> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
          final int _cursorIndexOfDone = CursorUtil.getColumnIndexOrThrow(_cursor, "done");
          final List<Item> _result = new ArrayList<Item>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Item _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpTime;
            _tmpTime = _cursor.getLong(_cursorIndexOfTime);
            final boolean _tmpDone;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDone);
            _tmpDone = _tmp != 0;
            _item = new Item(_tmpId,_tmpKind,_tmpTitle,_tmpTime,_tmpDone);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object upcoming(final long now, final Continuation<? super List<Item>> $completion) {
    final String _sql = "SELECT * FROM Item WHERE time>? AND done=0 AND kind!='study'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, now);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Item>>() {
      @Override
      @NonNull
      public List<Item> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
          final int _cursorIndexOfDone = CursorUtil.getColumnIndexOrThrow(_cursor, "done");
          final List<Item> _result = new ArrayList<Item>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Item _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpTime;
            _tmpTime = _cursor.getLong(_cursorIndexOfTime);
            final boolean _tmpDone;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDone);
            _tmpDone = _tmp != 0;
            _item = new Item(_tmpId,_tmpKind,_tmpTitle,_tmpTime,_tmpDone);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object lastMemos(final int n, final Continuation<? super List<Memo>> $completion) {
    final String _sql = "SELECT * FROM Memo ORDER BY id DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, n);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Memo>>() {
      @Override
      @NonNull
      public List<Memo> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfTs = CursorUtil.getColumnIndexOrThrow(_cursor, "ts");
          final List<Memo> _result = new ArrayList<Memo>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Memo _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRole;
            _tmpRole = _cursor.getString(_cursorIndexOfRole);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final long _tmpTs;
            _tmpTs = _cursor.getLong(_cursorIndexOfTs);
            _item = new Memo(_tmpId,_tmpRole,_tmpText,_tmpTs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
