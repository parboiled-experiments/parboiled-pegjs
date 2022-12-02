package org.parboiled.peg.util;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.parboiled.Node;
import org.parboiled.json.BaseJsonParser;
import org.parboiled.parserunners.ParseRunner;
import org.parboiled.support.ParsingResult;

public class ParseUtils {

    public static String read(URL url) {
        try {
            try (Stream<String> lines = Files.lines(Paths.get(url.toURI()))) {
                return lines.collect(Collectors.joining(System.lineSeparator()));
            }
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }
	}

    public static ParsingResult<?> parse(String input, Class<? extends BaseJsonParser> parserClass, boolean trace) {
		ParseRunner<?> runner = org.parboiled.util.ParseUtils.createParseRunner(trace, parserClass);
		ParsingResult<?> result = runner.run(input);
		return result;
	}

    public static void visitLeafs(Node<?> node, BiFunction<Node<?>, Integer, Boolean> fn) {
		visitLeafs(node, 0, fn);
	}

	public static void visitLeafs(Node<?> node, int level, BiFunction<Node<?>, Integer, Boolean> fn) {
		if (node.getChildren().isEmpty()) {
			fn.apply(node, level);
			return;
		}
		level++;
		for (Node<?> sub : node.getChildren()) {
			visitLeafs(sub, level, fn);
		}
	}

	public static String indent(int level) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < level; i++) {
			sb.append(" ");
		}
		return sb.toString();
	}

	public static void printNode(ParsingResult<?> result, Node<?> node, int level) {
		System.out.println();
		System.out.print(ParseUtils.indent(level));
		System.out.print(node.getLabel());
		String value = result.inputBuffer.extract(node.getStartIndex(), node.getEndIndex());
		System.out.print(" : " + value);
	}

}
