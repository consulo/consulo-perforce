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

import consulo.process.cmd.ParametersList;
import consulo.util.lang.StringUtil;
import consulo.util.lang.PatternUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class View {
    private final String myDepotPath;
    private final String myLocalPath;
    private final String myStringRepresentation;
    private final Mapping myDepotToLocalMapping;
    private final Mapping myLocalToDepotMapping;

    public static @Nullable View create(String viewString) {

        String[] params = ParametersList.parse(viewString);

        List<String> strings = Arrays.asList(params);
        if (strings.size() != 2) {
            return null;
        }
        String depot = strings.get(0).trim();
        String client = strings.get(1).trim();

        return new View(depot, client, viewString);
    }

    private View(String depotPath, String localPath, String viewString) {
        myDepotPath = depotPath;
        myLocalPath = localPath;
        myStringRepresentation = viewString;

        String rawDepot = depotPath.startsWith("-") || depotPath.startsWith("+") ? depotPath.substring(1) : depotPath;
        myDepotToLocalMapping = new Mapping(rawDepot, localPath);
        myLocalToDepotMapping = new Mapping(localPath, rawDepot);
    }

    public String getDepotPath() {
        return myDepotPath;
    }

    public String getLocalPath() {
        return myLocalPath;
    }


    @Override
    public String toString() {
        return myStringRepresentation;
    }

    public @Nullable String match(String filePath, String clientName) {
        String result = myDepotToLocalMapping.replaceSrcPathWithDest(filePath);
        if (result == null) {
            return null;
        }
        String clientPrefix = "//" + clientName + "/";
        return StringUtil.startsWithIgnoreCase(result, clientPrefix) ? result.substring(clientPrefix.length()) : result;
    }

    public boolean removeMatched() {
        return getDepotPath().startsWith("-");
    }

    public static @Nullable String getRelativePath(String filePath, @Nullable String clientName, List<? extends View> views) {
        String result = null;
        for (View view : views) {
            if (clientName != null) {
                String localResult = view.match(filePath, clientName);
                if (localResult != null) {
                    if (view.removeMatched()) {
                        result = null;
                    }
                    else {
                        result = localResult;
                    }
                }
            }
        }
        return result;
    }

    public static boolean isExcluded(String clientPath, List<? extends View> views) {
        for (int i = views.size() - 1; i >= 0; i--) {
            View view = views.get(i);
            if (view.myLocalToDepotMapping.replaceSrcPathWithDest(clientPath) != null) {
                return view.removeMatched();
            }
        }

        return false;
    }

    interface ReferenceVisitor {
        void visitNextReference(int referenceNum);
        void visitStringFragment(CharSequence string);
        void visitWildcard();
        void visitStar();
    }

    private static void processStringWithReferences(String stringWithReferences, ReferenceVisitor visitor) {
        int offset = 0;
        int refIndex = stringWithReferences.indexOf("%%", offset);
        int wildcardIndex = stringWithReferences.indexOf("\\.\\.\\.", offset);
        int starIndex = stringWithReferences.indexOf(".*", offset);

        while ((refIndex >= 0 && stringWithReferences.length() > refIndex + 2) || wildcardIndex >= 0 || starIndex >= 0) {

            if ( existsAndBefore(refIndex, wildcardIndex, starIndex) ) {

                // Process reference, i.e %%3 :
                if (refIndex > offset) {
                    visitor.visitStringFragment(stringWithReferences.subSequence(offset, refIndex));
                }
                String referenceName = stringWithReferences.substring(refIndex + 2, refIndex + 3);
                try {
                    visitor.visitNextReference(Integer.parseInt(referenceName));
                }
                finally {
                    offset = refIndex + 3;
                }
                refIndex = stringWithReferences.indexOf("%%", offset);
            }
            else if ( existsAndBefore(starIndex, wildcardIndex) ) {

                // Process star, i.e. *:
                if (starIndex > offset) {
                    visitor.visitStringFragment(stringWithReferences.subSequence(offset, starIndex));
                }
                try {
                    visitor.visitStar();
                }
                finally {
                    offset = starIndex + 2;
                }
                starIndex = stringWithReferences.indexOf(".*", offset);
            }

            else {

                // Process wildcard, i.e. ...
                if (wildcardIndex > offset) {
                    visitor.visitStringFragment(stringWithReferences.subSequence(offset, wildcardIndex));
                }
                try {
                    visitor.visitWildcard();
                }
                finally {
                    offset = wildcardIndex + 6;
                }
                wildcardIndex = stringWithReferences.indexOf("\\.\\.\\.", offset);

            }
        }
        if (offset < stringWithReferences.length()) {
            visitor.visitStringFragment(stringWithReferences.subSequence(offset, stringWithReferences.length()));
        }

    }

    private static boolean existsAndBefore(int index, int ... otherIndices) {
        if (index == -1) {
            return false;
        }
        for (int otherIdx : otherIndices) {
            if (otherIdx < index && otherIdx != -1) {
                return false;
            }
        }
        return true;
    }

    private static class Mapping {
        String mySrcMask;
        String myDestMask;
        private @Nullable Matcher mySrcMatcher;
        private Map<Integer, Integer> mySrcPatternBackReferences = new HashMap<>();
        private List<Integer> myWildcardGroupNumbers = new ArrayList<>();
        private List<Integer> myStarGroupNumbers = new ArrayList<>();
        private int mySrcPatternGroupCount;
        private @Nullable String myDestPattern;

        Mapping(String srcMask, String destMask) {
            mySrcMask = srcMask;
            myDestMask = destMask;
        }

        private synchronized @Nullable String replaceSrcPathWithDest(String filePath) {
            Matcher matcher = getMatcher(filePath);
            if (matcher.matches()) {
                return matcher.replaceFirst(convertDestMaskToReplacement(mySrcPatternBackReferences));
            }
            else {
                return null;
            }
        }

        private Matcher getMatcher(String filePath) {
            if (mySrcMatcher == null) {
                String srcPattern = PatternUtil.convertToRegex(mySrcMask);
                mySrcPatternBackReferences = new HashMap<>();
                myWildcardGroupNumbers = new ArrayList<>();
                myStarGroupNumbers = new ArrayList<>();
                mySrcPatternGroupCount = 1;
                StringBuffer patternWithReferences = new StringBuffer();
                processStringWithReferences(srcPattern, new ReferenceVisitor() {

                    @Override
                    public void visitWildcard() {
                        visitGroup("(.*)", myWildcardGroupNumbers);
                    }

                    @Override
                    public void visitStar() {
                        visitGroup("([^/]+)", myStarGroupNumbers);
                    }

                    private void visitGroup(String patternToAdd, List<Integer> groupNumbersCollection) {
                        patternWithReferences.append(patternToAdd);
                        groupNumbersCollection.add(mySrcPatternGroupCount);
                        mySrcPatternGroupCount++;

                    }

                    @Override
                    public void visitNextReference(int referenceNum) {
                        patternWithReferences.append("(.*)");
                        mySrcPatternBackReferences.put(referenceNum, mySrcPatternGroupCount);
                        mySrcPatternGroupCount++;
                    }

                    @Override
                    public void visitStringFragment(CharSequence string) {
                        patternWithReferences.append(string.toString());
                    }
                });
                srcPattern = patternWithReferences.toString();
                Pattern pattern = Pattern.compile(srcPattern, Pattern.CASE_INSENSITIVE);
                mySrcMatcher = pattern.matcher(filePath);
                return mySrcMatcher;
            }

            mySrcMatcher.reset(filePath);
            return mySrcMatcher;
        }

        private String convertDestMaskToReplacement(Map<Integer, Integer> backReferences) {
            StringBuilder result = new StringBuilder();

            processStringWithReferences(getDestPattern(), new MyReferenceVisitor(result, backReferences));
            return result.toString();
        }

        private String getDestPattern() {
            if (myDestPattern == null) {
                myDestPattern = PatternUtil.convertToRegex(myDestMask);
            }
            return myDestPattern;
        }

        private class MyReferenceVisitor implements ReferenceVisitor {
            private final StringBuilder myResult;
            private final Map<Integer, Integer> myBackReferences;
            private int myLastWildcardGroup = 0;
            private int myLastStarGroup = 0;

            MyReferenceVisitor(StringBuilder result, Map<Integer, Integer> backReferences) {
                myResult = result;
                myBackReferences = backReferences;
            }

            @Override
            public void visitWildcard() {
                myLastWildcardGroup = visitGroup(myLastWildcardGroup, myWildcardGroupNumbers);
            }

            @Override
            public void visitStar() {
                myLastStarGroup = visitGroup(myLastStarGroup, myStarGroupNumbers);
            }

            private int visitGroup(int counter, List<Integer> groupNumbers) {
                if (counter < groupNumbers.size()) {
                    myResult.append("$").append(groupNumbers.get(counter).intValue());
                    return counter + 1;
                }
                return counter;
            }

            @Override
            public void visitNextReference(int referenceNum) {
                Integer refGroupNum = myBackReferences.get(referenceNum);
                if (refGroupNum != null) {
                    myResult.append("$");
                    myResult.append(refGroupNum);
                }
                else {
                    myResult.append("%%");
                    myResult.append(referenceNum);
                }
            }

            @Override
            public void visitStringFragment(CharSequence string) {
                myResult.append(string.toString());
            }
        }
    }

}
