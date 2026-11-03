/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.perforce;


import org.jspecify.annotations.Nullable;

import java.util.Date;

public final class P4Revision {

    private final String myDepotPath;
    private final long myRevisionNumber;
    private final long myChangeNumber;
    private final String myAction;
    private final Date myDate;
    private final String myUser;
    private final String myClient;
    private final String myType;
    private @Nullable String mySubmitMessage = null;
    private final boolean myBranched;

    public P4Revision(String depotPath,
                                        long revisionNumber,
                                        long changeNumber,
                                        String action,
                                        Date date,
                                        String user,
                                        String client,
                                        String type,
                                        boolean branched) {
        myDepotPath = depotPath;
        myRevisionNumber = revisionNumber;
        myChangeNumber = changeNumber;
        myAction = action;
        myDate = date;
        myUser = user;
        myClient = client;
        myType = type;
        myBranched = branched;
    }

    public long getRevisionNumber() {
        return myRevisionNumber;
    }

    public String getDepotPath() {
        return myDepotPath;
    }

    public long getChangeNumber() {
        return myChangeNumber;
    }

    public String getAction() {
        return myAction;
    }

    public Date getDate() {
        return myDate;
    }

    public String getUser() {
        return myUser;
    }

    public String getClient() {
        return myClient;
    }

    public String getType() {
        return myType;
    }

    public @Nullable String getSubmitMessage() {
        return mySubmitMessage;
    }

    public void setDescription(String s) {
        mySubmitMessage = s;
    }

    public boolean isBranched() {
        return myBranched;
    }
}
