// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;


public enum P4Command {
    add("add"),
    ignores("ignores"),
    edit("edit"),
    revert("revert"),
    delete("delete"),
    integrate("integrate"),
    reopen("reopen"),
    clients("clients"),
    users("users"),
    user("user"),
    describe("describe"),
    change("change"),
    changes("changes"),
    have("have"),
    opened("opened"),
    submit("submit"),
    filelog("filelog"),
    sync("sync"),
    dirs("dirs"),
    files("files"),
    jobs("jobs"),
    resolved("resolved"),
    resolve("resolve"),
    move("move"),
    unknown("unknown");

    private final String myName;

    P4Command(String name) {
        myName = name;
    }

    public String getName() {
        return myName;
    }

    public static P4Command getInstance(String command) {
        String trimmed = command.trim();
        P4Command[] p4Commands = values();
        for (P4Command p4Command : p4Commands) {
            if (p4Command.getName().equals(trimmed)) {
                return p4Command;
            }
        }
        return unknown;
    }
}
