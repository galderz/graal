package com.oracle.graal.pointsto.reports;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.CallTreePrinter.InvokeNode;
import com.oracle.graal.pointsto.reports.CallTreePrinter.MethodNode;
import com.oracle.graal.pointsto.reports.CallTreePrinter.MethodNodeReference;
import com.oracle.graal.pointsto.reports.CallTreePrinter.Node;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CallTreeCypher {

    // TODO temporarily use a system property to try different values
    private static final int BATCH_SIZE = Integer.getInteger("cypher.batch.size", 256);

    private static final String METHOD_INFO_FORMAT = "properties:{name:'%n', signature:'" + CallTreePrinter.METHOD_FORMAT + "'}";

    private static final AtomicInteger virtualNodeId = new AtomicInteger(-1);

    public static void print(BigBang bigbang, String path, String reportName) {
        // Re-initialize method ids back to 0 to better diagnose disparities
        MethodNode.methodId = 0;

        CallTreePrinter printer = new CallTreePrinter(bigbang);
        printer.buildCallTree();

        // Set virtual node at next available method id
        virtualNodeId.set(MethodNode.methodId);

        Set<Integer> entryPoints = new HashSet<>();
        Map<Integer, Set<Integer>> directEdges = new HashMap<>();
        Map<Integer, Set<Integer>> virtualEdges = new HashMap<>();
        Map<Integer, Set<Integer>> overridenByEdges = new HashMap<>();
        Map<VirtualInvokeId, Integer> virtualNodes = new HashMap<>();
        calculateEdges(printer.methodToNode, entryPoints, directEdges, virtualEdges, overridenByEdges, virtualNodes);

        final String vmFileName = ReportUtils.report("call tree for vm entry point", path + File.separatorChar + "reports", "csv_call_tree_vm_" + reportName, "csv",
                CallTreeCypher::printVMEntryPoint);

        final String methodsFileName = ReportUtils.report("call tree for methods", path + File.separatorChar + "reports", "csv_call_tree_methods_" + reportName, "csv",
                writer -> printMethodNodes(printer.methodToNode.values(), writer));

        final String virtualMethodsFileName = ReportUtils.report("call tree for virtual methods", path + File.separatorChar + "reports", "csv_call_tree_virtual_methods_" + reportName, "csv",
                writer -> printVirtualNodes(virtualNodes, writer));

        final String entryPointsFileName = ReportUtils.report("call tree for entry points", path + File.separatorChar + "reports", "csv_call_tree_entry_points_" + reportName, "csv",
                writer -> printEntryPoints(entryPoints, writer));

        final String directEdgesFileName = ReportUtils.report("call tree for direct edges", path + File.separatorChar + "reports", "csv_call_tree_direct_edges_" + reportName, "csv",
                writer -> printEdges(directEdges, writer));

        final String overridenByEdgesFileName = ReportUtils.report("call tree for overriden by edges", path + File.separatorChar + "reports", "csv_call_tree_override_by_edges_" + reportName, "csv",
                writer -> printEdges(overridenByEdges, writer));

        final String virtualEdgesFileName = ReportUtils.report("call tree for virtual edges", path + File.separatorChar + "reports", "csv_call_tree_virtual_edges_" + reportName, "csv",
                writer -> printEdges(virtualEdges, writer));

        ReportUtils.report("call tree cypher", path + File.separatorChar + "reports", "cypher_call_tree_" + reportName, "cypher",
                writer -> printCypher(vmFileName, methodsFileName, virtualMethodsFileName, entryPointsFileName, directEdgesFileName, overridenByEdgesFileName, virtualEdgesFileName, writer));

//        ReportUtils.report("call tree cypher in batches of " + BATCH_SIZE, path + File.separatorChar + "reports", "call_tree_" + reportName, "cypher",
//                writer -> printCypher(printer.methodToNode, writer));
    }

    private static void printEntryPoints(Set<Integer> entryPoints, PrintWriter writer) {
        writer.println(convertToCSV("Id"));
        entryPoints.forEach(writer::println);
    }

    private static void printCypher(String vmFileName, String methodsFileName, String virtualMethodsFileName, String entryPointsFileName, String directEdgesFileName, String overridenByEdgesFileName, String virtualEdgesFileName, PrintWriter writer) {
        writer.println(String.format("LOAD CSV WITH HEADERS FROM 'file:///%s' AS row", vmFileName));
        writer.println("MERGE (v:VM {vmId: row.Id, name: row.Name});");

        writer.println(String.format("LOAD CSV WITH HEADERS FROM 'file:///%s' AS row", methodsFileName));
        writer.println("MERGE (m:Method {methodId: row.Id, name: row.Name, package: row.Package, parameters: row.Parameters, return: row.Return});");

        writer.println(String.format("LOAD CSV WITH HEADERS FROM 'file:///%s' AS row", virtualMethodsFileName));
        writer.println("MERGE (m:Method {methodId: row.Id, name: row.Name, package: row.Package, parameters: row.Parameters, return: row.Return});");

        writer.println(String.format("LOAD CSV WITH HEADERS FROM 'file:///%s' AS row", entryPointsFileName));
        writer.println("MATCH (m:Method {methodId: row.Id})");
        writer.println("MATCH (v:VM {vmId: '0'})");
        writer.println("MERGE (v)-[:ENTRY]->(m);");

        writer.println(String.format("LOAD CSV WITH HEADERS FROM 'file:///%s' AS row", directEdgesFileName));
        writer.println("MATCH (m1:Method {methodId: row.StartId})");
        writer.println("MATCH (m2:Method {methodId: row.EndId})");
        writer.println("MERGE (m1)-[:DIRECT]->(m2);");

        writer.println(String.format("LOAD CSV WITH HEADERS FROM 'file:///%s' AS row", overridenByEdgesFileName));
        writer.println("MATCH (m1:Method {methodId: row.StartId})");
        writer.println("MATCH (m2:Method {methodId: row.EndId})");
        writer.println("MERGE (m1)-[:OVERRIDEN_BY]->(m2);");

        writer.println(String.format("LOAD CSV WITH HEADERS FROM 'file:///%s' AS row", virtualEdgesFileName));
        writer.println("MATCH (m1:Method {methodId: row.StartId})");
        writer.println("MATCH (m2:Method {methodId: row.EndId})");
        writer.println("MERGE (m1)-[:VIRTUAL]->(m2);");
    }

    private static void printVMEntryPoint(PrintWriter writer) {
        writer.println(convertToCSV("Id", "Name"));
        writer.println(convertToCSV("0", "VM"));
    }

//    private static void printCypher(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
//        writer.print(vmEntryPoint());
//        printMethodNodes(methodToNode.values(), writer);
////        printMethodEdges(methodToNode, writer);
//    }

    private static void printMethodNodes(Collection<MethodNode> methods, PrintWriter writer) {
        writer.println(convertToCSV("Id", "Name", "Package", "Parameters", "Return"));
        methods.stream()
                .map(CallTreeCypher::methodNodeInfo)
                .map(CallTreeCypher::convertToCSV)
                .forEach(writer::println);

//        final Collection<List<MethodNode>> methodsBatches = batched(methods);
//        final String unwindMethod = unwindMethod();
//        final String script = methodsBatches.stream()
//                .map(methodBatch -> methodBatch.stream()
//                        .map(method -> String.format("{_id:%d, %s}", method.id, method.method.format(METHOD_INFO_FORMAT)))
//                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
//                )
//                .collect(Collectors.joining(unwindMethod, "", unwindMethod));
//
//        writer.print(script);
    }

    private static String[] methodNodeInfo(MethodNode method) {
        return resolvedJavaTypeInfo(method.id, method.method);

//        // TODO method parameter types are opaque, but could in the future be split out and link together
//        //      e.g. each method could BELONG to a type, and a method could have PARAMETER relationships with N types
//        //      see https://neo4j.com/developer/guide-import-csv/#_converting_data_values_with_load_csv for examples
//        final String parameters =
//                method.method.getSignature().getParameterCount(false) > 0
//                ? method.method.format("%P").replace(",", "")
//                : "<empty>";
//
//        return new String[] {
//                String.valueOf(method.id),
//                method.method.getName(),
//                method.method.getDeclaringClass().toJavaName(true),
//                parameters,
//                method.method.getSignature().getReturnType(null).toJavaName(true),
//        };
    }

    private static String convertToCSV(String... data) {
//        return Stream.of(data)
//                .map(CallTreeCypher::escapeSpecialCharacters)
//                .collect(Collectors.joining(","));
        return String.join(",", data);
    }

    private static String escapeSpecialCharacters(String data) {
        if (data.contains(",")) {
            data = data.replace(",", "");
        }
        return data;
//        String escapedData = data.replaceAll("\\R", " ");
//        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
//            data = data.replace("\"", "\"\"");
//            escapedData = "\"" + data + "\"";
//        }
//        return escapedData;
    }

    private static void printVirtualNodes(Map<VirtualInvokeId, Integer> virtualNodes, PrintWriter writer) {
        writer.println(convertToCSV("Id", "Name", "Package", "Parameters", "Return"));
        virtualNodes.entrySet().stream()
                .map(CallTreeCypher::virtualMethodAndIdInfo)
                .map(CallTreeCypher::convertToCSV)
                .forEach(writer::println);

//        final Collection<List<Map.Entry<VirtualInvokeId, Integer>>> virtualNodesBatches = batched(virtualNodes.entrySet());
//        final String unwindMethod = unwindMethod();
//        final String script = virtualNodesBatches.stream()
//                .map(virtualNodesBatch -> virtualNodesBatch.stream()
//                        .map(virtualNode -> String.format("{_id:%d, %s}", virtualNode.getValue(), virtualNode.getKey().methodInfo))
//                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
//                )
//                .collect(Collectors.joining(unwindMethod, "", unwindMethod));
//        writer.print(script);
    }

    private static String[] virtualMethodAndIdInfo(Map.Entry<VirtualInvokeId, Integer> entry) {
        entry.getKey().methodInfo[0] = entry.getValue().toString();
        return entry.getKey().methodInfo;
    }

    private static String vmEntryPoint() {
        return ":begin\n" +
                "CREATE (v:VM {name: 'VM'});\n" +
                ":commit\n\n";
    }

    private static String unwindMethod() {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id})\n" +
                "  SET n += row.properties SET n:Method;\n" +
                ":commit\n\n";
    }

    private static void calculateEdges(Map<AnalysisMethod, MethodNode> methodToNode, Set<Integer> entryPoints, Map<Integer, Set<Integer>> directEdges, Map<Integer, Set<Integer>> virtualEdges, Map<Integer, Set<Integer>> overridenByEdges, Map<VirtualInvokeId, Integer> virtualNodes) {
        final Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
        while (iterator.hasNext()) {
            final MethodNode method = iterator.next();
            entryPoints.add(method.id);
            // writer.print(entryEdge(method.method.format(CallTreePrinter.METHOD_FORMAT)));
            addMethodEdges(method, directEdges, virtualEdges, overridenByEdges, virtualNodes);
        }
    }

//    private static void printMethodEdges(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
//        Map<VirtualInvokeId, Integer> virtualNodes = new HashMap<>();
//
//        Map<Integer, Set<Integer>> directEdges = new HashMap<>();
//        Map<Integer, Set<Integer>> virtualEdges = new HashMap<>();
//        Map<Integer, Set<Integer>> overridenByEdges = new HashMap<>();
//
//        final Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
//        while (iterator.hasNext()) {
//            final MethodNode node = iterator.next();
//            writer.print(entryEdge(node.method.format(CallTreePrinter.METHOD_FORMAT)));
//            addMethodEdges(node, directEdges, virtualEdges, overridenByEdges, virtualNodes);
//        }
//
//        printVirtualNodes(virtualNodes, writer);
//        printEdges("DIRECT", directEdges, writer);
//        printEdges("VIRTUAL", virtualEdges, writer);
//        printEdges("OVERRIDDEN_BY", overridenByEdges, writer);
//    }

    private static String entryEdge(String signature) {
        return "\n\nMATCH (v:VM),(m:Method)\n" +
                "  WHERE v.name = 'VM' AND m.signature = '" + signature + "'\n" +
                "CREATE (v)-[r:ENTRY]->(m);\n\n";
    }

    private static void addMethodEdges(MethodNode methodNode, Map<Integer, Set<Integer>> directEdges, Map<Integer, Set<Integer>> virtualEdges, Map<Integer, Set<Integer>> overridenByEdges, Map<VirtualInvokeId, Integer> virtualNodes) {
        for (InvokeNode invoke : methodNode.invokes) {
            if (invoke.isDirectInvoke) {
                if (invoke.callees.size() > 0) {
                    Node calleeNode = invoke.callees.get(0);
                    addMethodEdge(methodNode.id, calleeNode, directEdges);
                    if (calleeNode instanceof MethodNode) {
                        addMethodEdges((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes);
                    }
                }
            } else {
                final int virtualNodeId = addVirtualNode(invoke, virtualNodes);
                addVirtualMethodEdge(methodNode.id, virtualNodeId, virtualEdges);
                for (Node calleeNode : invoke.callees) {
                    addMethodEdge(virtualNodeId, calleeNode, overridenByEdges);
                    if (calleeNode instanceof MethodNode) {
                        addMethodEdges((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes);
                    }
                }
            }
        }
    }

    private static int addVirtualNode(InvokeNode node, Map<VirtualInvokeId, Integer> virtualNodes) {
//        final String methodInfo = node.targetMethod.format(METHOD_INFO_FORMAT);
//        final List<String> methodInfo = List.of()
        final String[] methodInfo = virtualMethodInfo(node.targetMethod);
        final List<Integer> bytecodeIndexes = Stream.of(node.sourceReferences)
                .map(source -> source.bci)
                .collect(Collectors.toList());

        final VirtualInvokeId id = new VirtualInvokeId(methodInfo, bytecodeIndexes);
        return virtualNodes.computeIfAbsent(id, k -> CallTreeCypher.virtualNodeId.getAndIncrement());
    }

    private static String[] virtualMethodInfo(AnalysisMethod method) {
        return resolvedJavaTypeInfo(null, method);
    }

    private static String[] resolvedJavaTypeInfo(Integer id, ResolvedJavaMethod method) {
        // TODO method parameter types are opaque, but could in the future be split out and link together
        //      e.g. each method could BELONG to a type, and a method could have PARAMETER relationships with N types
        //      see https://neo4j.com/developer/guide-import-csv/#_converting_data_values_with_load_csv for examples
        final String parameters =
                method.getSignature().getParameterCount(false) > 0
                        ? method.format("%P").replace(",", "")
                        : "empty";

        return new String[] {
                id == null ? null : Integer.toString(id),
                method.getName(),
                method.getDeclaringClass().toJavaName(true),
                parameters,
                method.getSignature().getReturnType(null).toJavaName(true),
        };
    }

    private static void addVirtualMethodEdge(int startId, int endId, Map<Integer, Set<Integer>> edges) {
        Set<Integer> nodeEdges = edges.computeIfAbsent(startId, k -> new HashSet<>());
        nodeEdges.add(endId);
    }

    private static void addMethodEdge(int nodeId, Node calleeNode, Map<Integer, Set<Integer>> edges) {
        Set<Integer> nodeEdges = edges.computeIfAbsent(nodeId, k -> new HashSet<>());
        MethodNode methodNode = calleeNode instanceof MethodNode
                ? (MethodNode) calleeNode
                : ((MethodNodeReference) calleeNode).methodNode;
        nodeEdges.add(methodNode.id);
    }

    private static void printEdges(Map<Integer, Set<Integer>> edges, PrintWriter writer) {
        final Set<Edge> idEdges = edges.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(endId -> new Edge(entry.getKey(), endId)))
                .collect(Collectors.toSet());

        writer.println(convertToCSV("StartId", "EndId"));
        idEdges.stream()
                .map(edge -> convertToCSV(String.valueOf(edge.startId), String.valueOf(edge.endId)))
                .forEach(writer::println);

//        final Collection<List<Edge>> batchedEdges = batched(idEdges);
//
//        final String unwindEdge = edge(edgeName);
//        final String script = batchedEdges.stream()
//                .map(edgesBatch -> edgesBatch.stream()
//                        .map(edge -> String.format("{start: {_id:%d}, end: {_id:%d}}", edge.startId, edge.endId))
//                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
//                )
//                .collect(Collectors.joining(unwindEdge, "", unwindEdge));
//
//        writer.print(script);
    }

    private static String edge(String edgeName) {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "MATCH (start:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.start._id})\n" +
                "MATCH (end:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.end._id})\n" +
                "CREATE (start)-[r:" + edgeName + "]->(end);\n" +
                ":commit\n\n";
    }

    private static <E> Collection<List<E>> batched(Collection<E> methods) {
        final AtomicInteger counter = new AtomicInteger();
        return methods.stream()
                .collect(Collectors.groupingBy(m -> counter.getAndIncrement() / BATCH_SIZE))
                .values();
    }

    private static final class Edge {
        final int startId;
        final int endId;

        private Edge(int startId, int endId) {
            this.startId = startId;
            this.endId = endId;
        }
    }

    private static final class VirtualInvokeId {
        final String[] methodInfo;
        final List<Integer> bytecodeIndexes;

        private VirtualInvokeId(String[] methodInfo, List<Integer> bytecodeIndexes) {
            this.methodInfo = methodInfo;
            this.bytecodeIndexes = bytecodeIndexes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VirtualInvokeId that = (VirtualInvokeId) o;
            return Arrays.equals(methodInfo, that.methodInfo) &&
                    bytecodeIndexes.equals(that.bytecodeIndexes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(bytecodeIndexes);
            result = 31 * result + Arrays.hashCode(methodInfo);
            return result;
        }
    }
}
