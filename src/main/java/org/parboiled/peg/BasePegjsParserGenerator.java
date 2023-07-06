package org.parboiled.peg;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.parboiled.Node;
import org.parboiled.support.ParsingResult;

public abstract class BasePegjsParserGenerator {

	protected static List<String> HIDE_LABELS = new LinkedList<>();
	static {
		HIDE_LABELS.add("start");
		HIDE_LABELS.add("OneOrMore");
		HIDE_LABELS.add("Sequence");
		HIDE_LABELS.add("FirstOf");

		HIDE_LABELS.add("WhiteSpace");
		HIDE_LABELS.add("LineTerminatorSequence");
		HIDE_LABELS.add("Comment");
		HIDE_LABELS.add("EOS");
		HIDE_LABELS.add("Initializer");
		HIDE_LABELS.add("CodeBlock");
	}

	protected static String generate(String start, ParsingResult<?> result) {
		StringBuilder sb = new StringBuilder();
		sb.append("package org.parboiled.peg;\n");
		sb.append("\n");
		sb.append("import org.parboiled.BaseParser;\n");
		sb.append("import org.parboiled.Parboiled;\n");
		sb.append("import org.parboiled.Rule;\n");
		sb.append("import org.parboiled.annotations.BuildParseTree;\n");
		sb.append("import org.parboiled.parserunners.BasicParseRunner;\n");
		sb.append("import org.parboiled.support.ParseTreeUtils;\n");
		sb.append("import org.parboiled.support.ParsingResult;\n");
		sb.append("\n");
		sb.append("@BuildParseTree\n");
		sb.append("public class Parser extends BaseParser<Object> {\n");
		sb.append("\n");
		sb.append("	public static void main(String[] args) {\n");
		sb.append("		ParsingResult<?> result = parse(\"Select * From Employee\");\n");
		sb.append("		System.out.println(ParseTreeUtils.printNodeTree(result));\n");
		sb.append("	}\n");
		sb.append("\n");
		sb.append("	public static ParsingResult<?> parse(String input) {\n");
		sb.append("		Parser parser = Parboiled.createParser(Parser.class);\n");
		sb.append("		BasicParseRunner<?> runner = new BasicParseRunner<Object>(parser." + start + "());\n");
		sb.append("		ParsingResult<?> result = runner.run(input);\n");
		sb.append("		System.out.println(\"tree:\" +ParseTreeUtils.printNodeTree(result));\n");
		sb.append("		return result;\n");
		sb.append("	}\n");
		sb.append("\n");

		sb.append(generateRule(result, result.parseTreeRoot));

		sb.append("	\n");
		sb.append("}");
		return sb.toString();
	}

	protected static StringBuilder generateRule(ParsingResult<?> result, Node<?> parent) {
		StringBuilder rule = new StringBuilder();

		if ("Rule".equals(parent.getLabel())) {

			String label = null;
			StringBuilder expression = null;
			for (Node<?> child : parent.getChildren()) {
				if ("IdentifierName".equals(child.getLabel())) {
					label = result.inputBuffer.extract(child.getStartIndex(), child.getEndIndex());
				} else if ("ChoiceExpression".equals(child.getLabel())) {
					expression = generateRule(result, child);
				}
			}

			rule.append("    public Rule " + label + "() { \n");
			rule.append("        return " + expression + "; \n");
			rule.append("    } \n\n");
			
//			rule.append("    public Rule " + label + " = " + expression + ";\n\n");
		}

		else if ("ChoiceExpression".equals(parent.getLabel())) {

			List<StringBuilder> childRules = generateRules(result, parent.getChildren());
			if (childRules.size() == 1) {
				rule.append(childRules.get(0));
			} else {
				rule.append("FirstOf(");
				joinChildRules(rule, childRules, ",");
				rule.append(")");
			}
		}

		else if ("SequenceExpression".equals(parent.getLabel())) {
			List<StringBuilder> childRules = generateRules(result, parent.getChildren());
			if (childRules.size() == 1) {
				rule.append(childRules.get(0));
			} else {
				rule.append("Sequence(");
				joinChildRules(rule, childRules, ",");
				rule.append(")");
			}
		}

		else if ("LabelColon".equals(parent.getLabel())) {
			// NoOps
		}

		else if ("PrefixedExpression".equals(parent.getLabel())) {

			Node<?> firstChild = parent.getChildren().get(0);
			if ("SuffixedExpression".equals(firstChild.getLabel())) {
				rule.append(generateRule(result, firstChild));
			} else {
				char prefix = 0;
				StringBuilder expression = null;
				for (Node<?> child : firstChild.getChildren()) {
					if ("PrefixedOperator".equals(child.getLabel())) {
						prefix = result.inputBuffer.charAt(child.getStartIndex());
					} else if ("SuffixedExpression".equals(child.getLabel())) {
						expression = generateRule(result, child);
					}
				}

				rule.append(prefixExpression(prefix, expression));
			}

		} else if ("SuffixedExpression".equals(parent.getLabel())) {

			Node<?> firstChild = parent.getChildren().get(0);
			if ("PrimaryExpression".equals(firstChild.getLabel())) {
				rule.append(generateRule(result, firstChild));
			} else {
				char suffix = 0;
				StringBuilder expression = null;
				for (Node<?> child : firstChild.getChildren()) {
					if ("SuffixedOperator".equals(child.getLabel())) {
						suffix = result.inputBuffer.charAt(child.getStartIndex());
					} else if ("PrimaryExpression".equals(child.getLabel())) {
						expression = generateRule(result, child);
					}
				}

				rule.append(suffixExpression(suffix, expression));
			}

		} else if ("LiteralMatcher".equals(parent.getLabel())) {
			boolean ic = false;
			String value = null;
			for (Node<?> child : parent.getChildren()) {
				if ("StringLiteral".equals(child.getLabel())) {
					value = result.inputBuffer.extract(child.getStartIndex() + 1, child.getEndIndex() - 1);
				} else if ("'i'".equals(child.getLabel())) {
					ic = true;
				}
			}

			if (ic) {
				rule.append("IgnoreCase(\"" + value + "\")");
			} else {
				rule.append("\"" + value + "\"");
			}
		}

		else if ("CharacterClassMatcher".equals(parent.getLabel())) {

			boolean invert = false;
			StringBuilder chars = new StringBuilder();

			for (Node<?> child : parent.getChildren()) {

				if ("FirstOf".equals(child.getLabel())) {
					String value = result.inputBuffer.extract(child.getStartIndex(), child.getEndIndex());
					if (value.length() < 3) {
						char char0 = value.charAt(0);
						if (char0 == '\\') {
							chars.append(escapeChar(value.charAt(1)));
						} else {
							chars.append(char0);
						}
					} else {
						char start = value.charAt(0);
						char end = value.charAt(2);
						for (char j = start; j <= end; j++) {
							chars.append(j);
						}
					}
				} else if ("'^'".equals(child.getLabel())) {
					invert = true;
				} else if ("'i'".equals(child.getLabel())) {
					// TODO handle ignore case
				}
			}

			if (invert) {
				rule.append("NoneOf(\"" + chars.toString() + "\").skipNode()");
			} else {
				rule.append("AnyOf(\"" + chars.toString() + "\").skipNode()");
			}
		}

		else if ("AnyMatcher".equals(parent.getLabel())) {
			rule.append("ANY");
		}

		else if ("RuleReferenceExpression".equals(parent.getLabel())) {
			String proxyName = null;
			for (Node<?> child : parent.getChildren()) {
				if ("IdentifierName".equals(child.getLabel())) {
					proxyName = result.inputBuffer.extract(child.getStartIndex(), child.getEndIndex());
				}
			}
			rule.append(proxyName + "()");
		}

		else {

			if (!parent.getChildren().isEmpty()) {
				List<StringBuilder> childRules = generateRules(result, parent.getChildren());

				if (childRules.size() > 0) {
					joinChildRules(rule, childRules, "");
					if (!HIDE_LABELS.contains(parent.getLabel())) {
						System.err.println(parent.getLabel());
					}
				}
			}

		}

		return rule;

	}

	protected static void joinChildRules(StringBuilder rule, List<StringBuilder> childRules, String delim) {
		Iterator<StringBuilder> iter = childRules.iterator();
		rule.append(iter.next());
		while (iter.hasNext()) {
			rule.append(delim).append(iter.next());
		}
	}

	protected static char escapeChar(char char1) {
		switch (char1) {
		case 'b':
			return '\b';
		case 'f':
			return '\f';
		case 'n':
			return '\n';
		case 'r':
			return '\r';
		case 't':
			return '\t';
		default:
			return char1;
		}
	}

	protected static String prefixExpression(char prefix, StringBuilder expression) {
		switch (prefix) {
		case '&':
			return "Test(" + expression + ")";
		case '!':
			return "TestNot(" + expression + ")";
		default:
			return expression.toString();
		}
	}

	protected static String suffixExpression(char suffix, StringBuilder expression) {
		switch (suffix) {
		case '?':
			return "Optional(" + expression + ")";
		case '*':
			return "ZeroOrMore(" + expression + ")";
		case '+':
			return "OneOrMore(" + expression + ")";
		default:
			return expression.toString();
		}
	}

	protected static List<StringBuilder> generateRules(ParsingResult<?> result, List<?> children) {
		List<StringBuilder> childRules = new LinkedList<>();
		for (Object sub : children) {
			StringBuilder childRule = generateRule(result, (Node<?>) sub);
			if (childRule.length() > 0) {
				childRules.add(childRule);
			}
		}
		return childRules;
	}

}