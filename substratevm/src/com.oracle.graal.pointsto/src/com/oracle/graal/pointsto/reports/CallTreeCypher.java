package com.oracle.graal.pointsto.reports;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.oracle.graal.pointsto.reports.ReportUtils.methodComparator;

public class CallTreeCypher {

    public static void print(BigBang bigbang, String path, String reportName) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timeStamp = LocalDateTime.now().format(formatter);

        final String cypherFileName = String.format("call_tree_%s_%s.cypher", reportName, timeStamp);
        final Path reportPath = Paths.get(path, "reports", cypherFileName);
        System.out.printf("Printing call tree cypher to %s%n", reportPath);

        final int batchSize = 2;
        final AtomicInteger counter = new AtomicInteger();

        // TODO there are probably faster ways to split in batches
        final Collection<List<AnalysisMethod>> methods = bigbang.getUniverse().getMethods().stream()
                .distinct()
                .sorted(methodComparator)
                .collect(Collectors.groupingBy(m -> counter.getAndIncrement() / batchSize))
                .values();

        final List<String> lines = methods.stream()
                .flatMap(methodBatch -> {
                    final List<String> cypherBatch = new ArrayList<>();
                    cypherBatch.add(":begin");
                    cypherBatch.add("UNWIND [");
                    cypherBatch.add(toCypherNode(methodBatch));
                    cypherBatch.add("] as row");
                    cypherBatch.add("CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id})");
                    cypherBatch.add("  SET n += row.properties SET n:Method;");
                    cypherBatch.add(":commit");
                    return cypherBatch.stream();
                })
                .collect(Collectors.toList());

        try {
            Files.write(reportPath, lines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        // TODO why not use distinct? Needs MethodNode.equals()
//        Map<AnalysisMethod, MethodNode> methodToNode = new LinkedHashMap<>();
//
//        // Add all the roots to the tree
//        bigbang.getUniverse().getMethods().stream()
//                .filter(m -> m.isRootMethod() && !methodToNode.containsKey(m))
//                .sorted(methodComparator)
//                .forEach(method ->
//                        // methodToNode.put(method, new MethodNode(method, true))
//                        System.out.println(method)
//                );
    }

    private static String toCypherNode(List<AnalysisMethod> methods) {
        return methods.stream()
                .map(method -> String.format("{_id:%d, properties:{name:'%s'}}", method.getId(), method.getName()))
                .collect(Collectors.joining(System.lineSeparator() + ","));
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
    
}
