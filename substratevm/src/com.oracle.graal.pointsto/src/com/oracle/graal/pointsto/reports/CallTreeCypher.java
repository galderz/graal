package com.oracle.graal.pointsto.reports;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.CallTreePrinter.MethodNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.oracle.graal.pointsto.reports.ReportUtils.methodComparator;

public class CallTreeCypher {

    public static void print(BigBang bigbang, String path, String reportName) {
        Map<AnalysisMethod, MethodNode> methodToNode = new LinkedHashMap<>();

        // Add all the roots to the tree
        bigbang.getUniverse().getMethods().stream()
                .filter(m -> m.isRootMethod() && !methodToNode.containsKey(m))
                .sorted(methodComparator)
                .forEach(method ->
                        // methodToNode.put(method, new MethodNode(method, true))
                        System.out.println(method)
                );

        
    }

}
