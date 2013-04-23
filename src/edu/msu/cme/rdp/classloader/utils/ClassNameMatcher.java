/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.msu.cme.rdp.classloader.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author fishjord
 */
public class ClassNameMatcher {

    private static class Match {

        public String name;
        public int dist;

        public Match(String name, int dist) {
            this.name = name;
            this.dist = dist;
        }
    }

    public static class ClassNameMatches {
        private List<String> exactMatches;
        private List<String> canonicalMatches;
        private List<String> nameMatches;

        public ClassNameMatches(List<String> exactMatches, List<String> canonicalMatches, List<String> nameMatches) {
            this.canonicalMatches = canonicalMatches;
            this.nameMatches = nameMatches;
            this.exactMatches = exactMatches;
        }

        public List<String> getCanonicalMatches() {
            return canonicalMatches;
        }

        public List<String> getNameMatches() {
            return nameMatches;
        }

        public List<String> getExactMatches() {
            return exactMatches;
        }
    }

    public static ClassNameMatches findClassNameMatches(String nonExistantClass, Set<String> knownClasses, int knn) {
        List<Match> canonicalMatches = new ArrayList();
        List<Match> nameMatches = new ArrayList();
        List<String> exactMatches = new ArrayList();
        nonExistantClass = nonExistantClass.replace(".", "/");

        String cName = nonExistantClass;
        if (cName.contains("/")) {
            cName = cName.substring(cName.lastIndexOf("/") + 1);
        }

        for (String knownClass : knownClasses) {
            String knownClassName = knownClass;
            if (knownClassName.contains("/")) {
                knownClassName = knownClassName.substring(knownClassName.lastIndexOf("/") + 1);
            }

            canonicalMatches.add(new Match(knownClass, StringUtils.getLevenshteinDistance(nonExistantClass, knownClass)));

            int mscore = StringUtils.getLevenshteinDistance(cName, knownClassName);
            if(mscore == 0) {
                exactMatches.add(knownClass);
            }
            nameMatches.add(new Match(knownClass, StringUtils.getLevenshteinDistance(cName, knownClassName)));
        }

        Comparator<Match> matchComparator = new Comparator<Match>() {

            public int compare(Match o1, Match o2) {
                return o1.dist - o2.dist;
            }
        };

        Collections.sort(canonicalMatches, matchComparator);
        Collections.sort(nameMatches, matchComparator);

        List<String> canonicalMatchRet = new ArrayList();
        int subListMax = Math.min(canonicalMatches.size(), knn);

        for (Match m : canonicalMatches.subList(0, subListMax)) {
            canonicalMatchRet.add(m.name);
        }

        List<String> nameMatchRet = new ArrayList();
        subListMax = Math.min(nameMatches.size(), knn);

        for (Match m : nameMatches.subList(0, subListMax)) {
            nameMatchRet.add(m.name);
        }

        return new ClassNameMatches(exactMatches, canonicalMatchRet, nameMatchRet);
    }

}
