package org.parboiled.peg;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;
import org.parboiled.annotations.DontLabel;
import org.parboiled.json.PegParser;
import org.parboiled.support.ParseTreeUtils;
import org.parboiled.support.ParsingResult;
import org.parboiled.util.ParseUtils;

@BuildParseTree
public class PegPegjsParser extends PegParser {

	private static List<String> HIDE_LABELS = new LinkedList<>();
	static {
		HIDE_LABELS.add("start");

		HIDE_LABELS.add("OneOrMore");
		HIDE_LABELS.add("FirstOf");
		HIDE_LABELS.add("Sequence");

		HIDE_LABELS.add("WhiteSpace");
		HIDE_LABELS.add("LineTerminatorSequence");
		HIDE_LABELS.add("EOS");
	}

	private static List<String> SKIP_LABELS = new LinkedList<>();
	static {
	}

	private static List<String> SUPRESS_LABELS = new LinkedList<>();
	static {
	}

	private static List<String> SUPRESS_SUB_LABELS = new LinkedList<>();
	static {

		SUPRESS_SUB_LABELS.add("WhiteSpace");
		SUPRESS_SUB_LABELS.add("LineTerminatorSequence");
		SUPRESS_SUB_LABELS.add("Comment");
		SUPRESS_SUB_LABELS.add("CodeBlock");

		SUPRESS_SUB_LABELS.add("IdentifierName");

		SUPRESS_SUB_LABELS.add("ClassCharacterRange");
		SUPRESS_SUB_LABELS.add("ClassCharacter");
		SUPRESS_SUB_LABELS.add("StringLiteral");
	}

	private static Rule startRule;

	@Override
	public Rule start() {
		if (startRule == null) {
			InputStream inputStream = getClass().getClassLoader().getResourceAsStream("pegjs.peg.json");
			JsonObject json = Json.createReader(inputStream).readObject();
			startRule = start("Grammar", json);
		}
		return startRule;
	}

	@DontLabel
	protected Rule parseRule(JsonObject jsonObj) {
		Rule rule = super.parseRule(jsonObj);
		String type = jsonObj.getString("type");
		if ("rule_ref".equals(type)) {
			String name = jsonObj.getString("name");
			if (SUPRESS_LABELS.contains(name)) {
				rule.suppressNode();
			} else if (SUPRESS_SUB_LABELS.contains(name)) {
				rule.suppressSubnodes();
			} else if (SKIP_LABELS.contains(name)) {
				rule.skipNode();
			}
		}

		return rule;
	}

	public static ParsingResult<?> parse(String input) throws Exception {
		return ParseUtils.parse(input, PegPegjsParser.class, false);
//		return ParseUtils.parse(input, PegPegjsParser.class, true);
	}

	public static void printTree(ParsingResult<?> result) {

		org.parboiled.util.ParseUtils.visitTree(result.parseTreeRoot, (node, level) -> {
			if (HIDE_LABELS.contains(node.getLabel()))
				return true;
			System.out.print(level + " : ");
			System.out.print(ParseUtils.indent(level));
			String value = ParseTreeUtils.getNodeText(node, result.inputBuffer).trim();
			System.out.println(node.getLabel() + " : " + value);
			return true;
		});
	}

	public static void main(String[] args) throws Exception {
		String input = "a = [abc0-9]";
//		String input = " a = b1 b2 / c1 c2 ";
		printTree(parse(input));

	}

}