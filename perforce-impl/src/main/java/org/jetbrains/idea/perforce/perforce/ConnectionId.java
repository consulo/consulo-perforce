// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Tag;
import org.jspecify.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

@Tag("ID")
public class ConnectionId {
    public boolean myUseP4Config;
    public @Nullable String myP4ConfigFileName;
    public @Nullable String myWorkingDir;

    public ConnectionId(@Nullable String p4ConfigFileName, String workingDir) {
        myP4ConfigFileName = p4ConfigFileName;
        myWorkingDir = workingDir;
        myUseP4Config = true;
    }

    // singleton connection constructor
    public ConnectionId() {
        myUseP4Config = false;
        myP4ConfigFileName = null;
        myWorkingDir = null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConnectionId that = (ConnectionId)o;

        if (!myUseP4Config) {
            return !that.myUseP4Config;
        }
        else {
            if (myP4ConfigFileName != null ? !myP4ConfigFileName.equals(that.myP4ConfigFileName) : that.myP4ConfigFileName != null) {
                return false;
            }
            return !(myWorkingDir != null ? !myWorkingDir.equals(that.myWorkingDir) : that.myWorkingDir != null);
        }


    }

    @Override
    public int hashCode() {
        if (!myUseP4Config) {
            return 0;
        }
        else {
            int result = 0;
            result = 29 * result + (myP4ConfigFileName != null ? myP4ConfigFileName.hashCode() : 0);
            result = 29 * result + (myWorkingDir != null ? myWorkingDir.hashCode() : 0);
            return result;
        }
    }

    public void writeToStream(DataOutput stream) throws IOException {
        stream.writeByte(!myUseP4Config ? 0 : 1);
        if (myUseP4Config) {
            stream.writeUTF(StringUtil.notNullize(myP4ConfigFileName));
            stream.writeUTF(Objects.requireNonNull(myWorkingDir));
        }
    }

    public static ConnectionId readFromStream(DataInput stream) throws IOException {
        byte useP4Config = stream.readByte();
        if (useP4Config == 0) {
            return new ConnectionId();
        }
        String configFileName = stream.readUTF();
        String workingDir = stream.readUTF();
        return new ConnectionId(configFileName.isEmpty() ? null : configFileName, workingDir);
    }
}
