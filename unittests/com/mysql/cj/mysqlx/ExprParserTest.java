/*
  Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FLOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package com.mysql.cj.mysqlx;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.mysql.cj.core.exceptions.WrongArgumentException;
import com.mysql.cj.mysqlx.protobuf.MysqlxDatatypes.Any;
import com.mysql.cj.mysqlx.protobuf.MysqlxDatatypes.Scalar;
import com.mysql.cj.mysqlx.protobuf.MysqlxCrud.Column;
import com.mysql.cj.mysqlx.protobuf.MysqlxCrud.Order;
import com.mysql.cj.mysqlx.protobuf.MysqlxCrud.Projection;
import com.mysql.cj.mysqlx.protobuf.MysqlxCrud.UpdateOperation;
import com.mysql.cj.mysqlx.protobuf.MysqlxExpr.ColumnIdentifier;
import com.mysql.cj.mysqlx.protobuf.MysqlxExpr.DocumentPathItem;
import com.mysql.cj.mysqlx.protobuf.MysqlxExpr.Expr;

/**
 * Expression parser tests.
 */
public class ExprParserTest {

    /**
     * Check that a string doesn't parse.
     */
    private void checkBadParse(String s) {
        try {
            Expr e = new ExprParser(s).parse();
            System.err.println("Parsed as: " + e);
            fail("Expected exception while parsing: '" + s + "'");
        } catch (WrongArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testUnparseables() {
        checkBadParse("1ee1");
        checkBadParse("1 + ");
        checkBadParse("x 1,2,3)");
        checkBadParse("x(1,2,3");
        checkBadParse("x(1 2,3)");
        checkBadParse("x(1,, 2,3)");
        checkBadParse("x not y");
        checkBadParse("x like");
        checkBadParse("like");
        checkBadParse("like x");
        checkBadParse("x + interval 1 MACROsecond");
        checkBadParse("x + interval 1 + 1");
        checkBadParse("x * interval 1 hour");
        checkBadParse("1.1.1");
        checkBadParse("a@**");
        checkBadParse("a.b.c.d > 1");
        checkBadParse("a@[1.1]");
        checkBadParse("a@[-1]");
        checkBadParse("a@1");
        checkBadParse("a@.1");
        checkBadParse("a@a");
        checkBadParse("a@.+");
        checkBadParse("a@(x)");
        checkBadParse("\"xyz");
        checkBadParse("x between 1");
        checkBadParse("x.1 > 1");
        checkBadParse("x@ > 1");
        checkBadParse(":>1");
        checkBadParse(":1.1");
        // TODO: test bad JSON identifiers (quoting?)
    }

    /**
     * Check that a string parses and is reconstituted as a string that we expect. Futher we parse the canonical version to make sure it doesn't change.
     */
    private void checkParseRoundTrip(String input, String expected) {
        if (expected == null) {
            expected = input;
        }
        Expr expr = new ExprParser(input).parse();
        String canonicalized = ExprUnparser.exprToString(expr);
        assertEquals(expected, canonicalized);

        // System.err.println("Canonicalized: " + canonicalized);
        Expr expr2 = new ExprParser(canonicalized).parse();
        String recanonicalized = ExprUnparser.exprToString(expr2);
        assertEquals(expected, recanonicalized);
    }

    /**
     * Test that expressions parsed and serialize back to the expected form.
     */
    @Test
    public void testRoundTrips() {
        checkParseRoundTrip("now () - interval '10:20' hour_MiNuTe", "date_sub(now(), \"10:20\", \"HOUR_MINUTE\")");
        checkParseRoundTrip("now () - interval 1 hour - interval 2 minute - interval 3 second",
                "date_sub(date_sub(date_sub(now(), 1, \"HOUR\"), 2, \"MINUTE\"), 3, \"SECOND\")");
        // this needs parens around 1+1 in interval expression
        checkParseRoundTrip("a + interval 1 hour + 1 + interval (1 + 1) second", "date_add((date_add(a, 1, \"HOUR\") + 1), (1 + 1), \"SECOND\")");
        checkParseRoundTrip("a + interval 1 hour + 1 + interval 1 * 1 second", "date_add((date_add(a, 1, \"HOUR\") + 1), (1 * 1), \"SECOND\")");
        checkParseRoundTrip("now () - interval -2 day", "date_sub(now(), -2, \"DAY\")"); // interval exprs compile to date_add/date_sub calls
        checkParseRoundTrip("1", "1");
        checkParseRoundTrip("1^0", "(1 ^ 0)");
        checkParseRoundTrip("1e1", "10.0");
        checkParseRoundTrip("1e4", "10000.0");
        checkParseRoundTrip("12e-4", "0.0012");
        checkParseRoundTrip("a + 314.1592e-2", "(a + 3.141592)");
        checkParseRoundTrip("a + 0.0271e+2", "(a + 2.71)");
        checkParseRoundTrip("a + 0.0271e2", "(a + 2.71)");
        checkParseRoundTrip("10+1", "(10 + 1)");
        checkParseRoundTrip("(abC == 1)", "(abC == 1)");
        checkParseRoundTrip("(Func(abc)==1)", "(Func(abc) == 1)");
        checkParseRoundTrip("(abc == \"jess\")", "(abc == \"jess\")");
        checkParseRoundTrip("(abc == \"with \\\"\")", "(abc == \"with \"\"\")"); // we escape with two internal quotes
        checkParseRoundTrip("(abc != .10)", "(abc != 0.1)");
        checkParseRoundTrip("(abc != \"xyz\")", "(abc != \"xyz\")");
        checkParseRoundTrip("a + b * c + d", "((a + (b * c)) + d)"); // shows precedence and associativity
        checkParseRoundTrip("(a + b) * c + d", "(((a + b) * c) + d)");
        checkParseRoundTrip("(field not in ('a',func('b', 2.0),'c'))", "field not in(\"a\", func(\"b\", 2.0), \"c\")");
        checkParseRoundTrip("jess.age beTwEEn 30 and death", "(jess.age between 30 AND death)");
        checkParseRoundTrip("jess.age not BeTweeN 30 and death", "(jess.age not between 30 AND death)");
        checkParseRoundTrip("a + b * c + d", "((a + (b * c)) + d)");
        checkParseRoundTrip("x > 10 and Y >= -20", "((x > 10) && (Y >= -20))");
        checkParseRoundTrip("a is true and b is null and C + 1 > 40 and (time == now() or hungry())",
                "((((a is TRUE) && (b is NULL)) && ((C + 1) > 40)) && ((time == now()) || hungry()))");
        checkParseRoundTrip("a + b + -c > 2", "(((a + b) + -c) > 2)");
        checkParseRoundTrip("now () + b + c > 2", "(((now() + b) + c) > 2)");
        checkParseRoundTrip("now () + @.b + c > 2", "(((now() + @.b) + c) > 2)");
        checkParseRoundTrip("now () - interval +2 day > some_other_time() or something_else IS NOT NULL",
                "((date_sub(now(), 2, \"DAY\") > some_other_time()) || is_not(something_else, NULL))");
        checkParseRoundTrip("\"two quotes to one\"\"\"", null);
        checkParseRoundTrip("'two quotes to one'''", "\"two quotes to one'\"");
        checkParseRoundTrip("'different quote \"'", "\"different quote \"\"\"");
        checkParseRoundTrip("\"different quote '\"", "\"different quote '\"");
        checkParseRoundTrip("`ident`", "ident"); // doesn't need quoting
        checkParseRoundTrip("`ident```", "`ident```");
        checkParseRoundTrip("`ident\"'`", "`ident\"'`");
        checkParseRoundTrip(":0 > x and func(:3, :2, :1)", "((:0 > x) && func(:1, :2, :3))"); // serialized in order of position (needs mapped externally)
        checkParseRoundTrip("a > now() + interval (2 + x) MiNuTe", "(a > date_add(now(), (2 + x), \"MINUTE\"))");
        checkParseRoundTrip("a between 1 and 2", "(a between 1 AND 2)");
        checkParseRoundTrip("a not between 1 and 2", "(a not between 1 AND 2)");
        checkParseRoundTrip("a in (1,2,a.b(3),4,5,x)", "a in(1, 2, a.b(3), 4, 5, x)");
        checkParseRoundTrip("a not in (1,2,3,4,5,@.x)", "a not in(1, 2, 3, 4, 5, @.x)");
        checkParseRoundTrip("a like b escape c", "a like b ESCAPE c");
        checkParseRoundTrip("a not like b escape c", "a not like b ESCAPE c");
        checkParseRoundTrip("(1 + 3) in (3, 4, 5)", "(1 + 3) in(3, 4, 5)");
        checkParseRoundTrip("`a crazy \"function\"``'name'`(1 + 3) in (3, 4, 5)", "`a crazy \"function\"``'name'`((1 + 3)) in(3, 4, 5)");
        checkParseRoundTrip("a@.b", "a@.b");
        checkParseRoundTrip("a@.*", "a@.*");
        checkParseRoundTrip("a@[0].*", "a@[0].*");
        checkParseRoundTrip("a@**[0].*", "a@**[0].*");
        checkParseRoundTrip("@._id", "@._id");
        checkParseRoundTrip("@._id == :0", "(@._id == :0)");
        // TODO: this isn't serialized correctly by the unparser
        //checkParseRoundTrip("a@.b[0][0].c**.d.\"a weird\\\"key name\"", "");
    }

    /**
     * Explicit test inspecting the expression tree.
     */
    @Test
    public void testExprTree() {
        Expr expr = new ExprParser("a like 'xyz' and @.count > 10 + 1").parse();
        assertEquals(Expr.Type.OPERATOR, expr.getType());
        assertEquals("&&", expr.getOperator().getName());
        assertEquals(2, expr.getOperator().getParamCount());

        // check left side of AND: (a like 'xyz')
        Expr andLeft = expr.getOperator().getParam(0);
        assertEquals(Expr.Type.OPERATOR, andLeft.getType());
        assertEquals("like", andLeft.getOperator().getName());
        assertEquals(2, andLeft.getOperator().getParamCount());
        Expr identA = andLeft.getOperator().getParam(0);
        assertEquals(Expr.Type.IDENT, identA.getType());
        assertEquals("a", identA.getIdentifier().getName());
        Expr literalXyz = andLeft.getOperator().getParam(1);
        assertEquals(Expr.Type.LITERAL, literalXyz.getType());
        assertEquals(Any.Type.SCALAR, literalXyz.getConstant().getType());
        Scalar scalarXyz = literalXyz.getConstant().getScalar();
        assertEquals(Scalar.Type.V_STRING, scalarXyz.getType());
        assertEquals("xyz", scalarXyz.getVString().getValue().toStringUtf8());

        // check right side of AND: (@.count > 10 + 1)
        Expr andRight = expr.getOperator().getParam(1);
        assertEquals(Expr.Type.OPERATOR, andRight.getType());
        assertEquals(">", andRight.getOperator().getName());
        assertEquals(2, andRight.getOperator().getParamCount());
        Expr countDocPath = andRight.getOperator().getParam(0);
        assertEquals(Expr.Type.IDENT, countDocPath.getType());
        ColumnIdentifier countId = countDocPath.getIdentifier();
        assertFalse(countId.hasName());
        assertFalse(countId.hasTableName());
        assertFalse(countId.hasSchemaName());
        assertEquals(1, countId.getDocumentPathCount());
        assertEquals(DocumentPathItem.Type.MEMBER, countId.getDocumentPath(0).getType());
        assertEquals("count", countId.getDocumentPath(0).getValue());
        Expr addition = andRight.getOperator().getParam(1);
        Scalar addLeftScalar = addition.getOperator().getParam(0).getConstant().getScalar();
        Scalar addRightScalar = addition.getOperator().getParam(1).getConstant().getScalar();
        assertEquals(Expr.Type.OPERATOR, addition.getType());
        assertEquals("+", addition.getOperator().getName());
        assertEquals(2, addition.getOperator().getParamCount());
        assertEquals(Expr.Type.LITERAL, addition.getOperator().getParam(0).getType());
        assertEquals(Expr.Type.LITERAL, addition.getOperator().getParam(1).getType());
        assertEquals(Scalar.Type.V_SINT, addLeftScalar.getType());
        assertEquals(Scalar.Type.V_SINT, addRightScalar.getType());
        assertEquals(10, addLeftScalar.getVSignedInt());
        assertEquals(1, addRightScalar.getVSignedInt());
    }

    @Test
    public void testOrderByParserBasic() {
        List<Order> orderSpec = new ExprParser("a, b desc").parseOrderSpec();
        assertEquals(2, orderSpec.size());
        Order o1 = orderSpec.get(0);
        assertFalse(o1.hasDirection());
        assertEquals("a", ExprUnparser.exprToString(o1.getField()));
        Order o2 = orderSpec.get(1);
        assertTrue(o2.hasDirection());
        assertEquals(Order.Direction.DESC, o2.getDirection());
        assertEquals("b", ExprUnparser.exprToString(o2.getField()));
    }

    @Test
    public void testOrderByParserComplexExpressions() {
        List<Order> orderSpec = new ExprParser("field not in ('a',func('b', 2.0),'c') desc, 1-a@**[0].*, now () + @.b + c > 2 asc").parseOrderSpec();
        assertEquals(3, orderSpec.size());
        Order o1 = orderSpec.get(0);
        assertTrue(o1.hasDirection());
        assertEquals(Order.Direction.DESC, o1.getDirection());
        assertEquals("field not in(\"a\", func(\"b\", 2.0), \"c\")", ExprUnparser.exprToString(o1.getField()));
        Order o2 = orderSpec.get(1);
        assertFalse(o2.hasDirection());
        assertEquals("(1 - a@**[0].*)", ExprUnparser.exprToString(o2.getField()));
        Order o3 = orderSpec.get(2);
        assertTrue(o3.hasDirection());
        assertEquals(Order.Direction.ASC, o3.getDirection());
        assertEquals("(((now() + @.b) + c) > 2)", ExprUnparser.exprToString(o3.getField()));
    }

    @Test
    public void testNamedPlaceholders() {
        ExprParser parser = new ExprParser("a == :a and b == :b and (c == 'x' or d == :b)");
        Expr e = parser.parse();
        assertEquals(new Integer(0), parser.placeholderNameToPosition.get("a"));
        assertEquals(new Integer(1), parser.placeholderNameToPosition.get("b"));
        assertEquals(2, parser.positionalPlaceholderCount);

        Expr aEqualsPlaceholder = e.getOperator().getParam(0).getOperator().getParam(0).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, aEqualsPlaceholder.getType());
        assertEquals(0, aEqualsPlaceholder.getPosition());
        Expr bEqualsPlaceholder = e.getOperator().getParam(0).getOperator().getParam(1).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, bEqualsPlaceholder.getType());
        assertEquals(1, bEqualsPlaceholder.getPosition());
        Expr dEqualsPlaceholder = e.getOperator().getParam(1).getOperator().getParam(1).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, dEqualsPlaceholder.getType());
        assertEquals(1, dEqualsPlaceholder.getPosition());
    }

    @Test
    public void testNumberedPlaceholders() {
        ExprParser parser = new ExprParser("a == :1 and b == :3 and (c == :2 or d == :2)");
        Expr e = parser.parse();
        assertEquals(new Integer(0), parser.placeholderNameToPosition.get("1"));
        assertEquals(new Integer(1), parser.placeholderNameToPosition.get("3"));
        assertEquals(new Integer(2), parser.placeholderNameToPosition.get("2"));
        assertEquals(3, parser.positionalPlaceholderCount);

        Expr aEqualsPlaceholder = e.getOperator().getParam(0).getOperator().getParam(0).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, aEqualsPlaceholder.getType());
        assertEquals(0, aEqualsPlaceholder.getPosition());
        Expr bEqualsPlaceholder = e.getOperator().getParam(0).getOperator().getParam(1).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, bEqualsPlaceholder.getType());
        assertEquals(1, bEqualsPlaceholder.getPosition());
        Expr cEqualsPlaceholder = e.getOperator().getParam(1).getOperator().getParam(0).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, cEqualsPlaceholder.getType());
        assertEquals(2, cEqualsPlaceholder.getPosition());
        Expr dEqualsPlaceholder = e.getOperator().getParam(1).getOperator().getParam(1).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, dEqualsPlaceholder.getType());
        assertEquals(2, dEqualsPlaceholder.getPosition());
    }

    @Test
    public void testUnnumberedPlaceholders() {
        ExprParser parser = new ExprParser("a == ? and b == ? and (c == 'x' or d == ?)");
        Expr e = parser.parse();
        assertEquals(new Integer(0), parser.placeholderNameToPosition.get("0"));
        assertEquals(new Integer(1), parser.placeholderNameToPosition.get("1"));
        assertEquals(new Integer(2), parser.placeholderNameToPosition.get("2"));
        assertEquals(3, parser.positionalPlaceholderCount);

        Expr aEqualsPlaceholder = e.getOperator().getParam(0).getOperator().getParam(0).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, aEqualsPlaceholder.getType());
        assertEquals(0, aEqualsPlaceholder.getPosition());
        Expr bEqualsPlaceholder = e.getOperator().getParam(0).getOperator().getParam(1).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, bEqualsPlaceholder.getType());
        assertEquals(1, bEqualsPlaceholder.getPosition());
        Expr dEqualsPlaceholder = e.getOperator().getParam(1).getOperator().getParam(1).getOperator().getParam(1);
        assertEquals(Expr.Type.PLACEHOLDER, dEqualsPlaceholder.getType());
        assertEquals(2, dEqualsPlaceholder.getPosition());
    }

    @Test
    public void testTrivialDocumentProjection() {
        List<Projection> proj;

        proj = new ExprParser("@.a as @.a").parseDocumentProjection();
        assertEquals(1, proj.size());
        assertEquals(1, proj.get(0).getTargetPathCount());
        List<DocumentPathItem> paths = proj.get(0).getSource().getIdentifier().getDocumentPathList();
        assertEquals(1, paths.size());
        assertEquals(DocumentPathItem.Type.MEMBER, paths.get(0).getType());
        assertEquals("a", paths.get(0).getValue());

        proj = new ExprParser("@.a as @.a, @.b as @.b, @.c as @.c").parseDocumentProjection();
    }

    @Test
    public void testExprAsPathDocumentProjection() {
        List<Projection> projList = new ExprParser("@.a as @.b, (1 + 1) * 100 as @.x, 2 as @[42]").parseDocumentProjection();

        assertEquals(3, projList.size());

        // check @.a as @.b
        Projection p = projList.get(0);
        List<DocumentPathItem> paths = p.getSource().getIdentifier().getDocumentPathList();
        assertEquals(1, paths.size());
        assertEquals(DocumentPathItem.Type.MEMBER, paths.get(0).getType());
        assertEquals("a", paths.get(0).getValue());

        assertEquals(1, p.getTargetPathCount());
        paths = p.getTargetPathList();
        assertEquals(1, paths.size());
        assertEquals(DocumentPathItem.Type.MEMBER, paths.get(0).getType());
        assertEquals("b", paths.get(0).getValue());

        // check (1 + 1) * 100 as @.x
        p = projList.get(1);
        assertEquals("((1 + 1) * 100)", ExprUnparser.exprToString(p.getSource()));
        paths = p.getTargetPathList();
        assertEquals(1, paths.size());
        assertEquals(DocumentPathItem.Type.MEMBER, paths.get(0).getType());
        assertEquals("x", paths.get(0).getValue());

        // check 2
        p = projList.get(2);
        paths = p.getTargetPathList();
        assertEquals(1, p.getTargetPathCount());
        assertEquals(DocumentPathItem.Type.ARRAY_INDEX, paths.get(0).getType());
        assertEquals(42, paths.get(0).getIndex());
        assertEquals("2", ExprUnparser.exprToString(p.getSource()));
    }

    @Test
    public void testTableInsertProjection() {
        List<Column> cols = new ExprParser("a").parseTableInsertProjection();
        assertEquals(1, cols.size());
        assertEquals("a", cols.get(0).getName());

        cols = new ExprParser("a, `double weird `` string`, c").parseTableInsertProjection();
        assertEquals(3, cols.size());
        assertEquals("a", cols.get(0).getName());
        assertEquals("double weird ` string", cols.get(1).getName());
        assertEquals("c", cols.get(2).getName());
    }

    @Test
    public void testTableUpdateProjection() {
        List<ColumnIdentifier> cols = new ExprParser("a, b.c, d.e@.the_path[2], `zzz\\``").parseTableUpdateProjection();
        assertEquals(4, cols.size());
        assertEquals("a", cols.get(0).getName());
        assertEquals("b", cols.get(1).getTableName());
        assertEquals("c", cols.get(1).getName());
        assertEquals("d", cols.get(2).getTableName());
        assertEquals("e", cols.get(2).getName());
        assertEquals(2, cols.get(2).getDocumentPathCount());
        assertEquals("the_path", cols.get(2).getDocumentPath(0).getValue());
        assertEquals(2, cols.get(2).getDocumentPath(1).getIndex());
        assertEquals("zzz`", cols.get(3).getName());
    }

    @Test
    public void testTrivialTableSelectProjection() {
        List<Projection> proj = new ExprParser("a, b as c").parseTableSelectProjection();
        assertEquals(2, proj.size());
        assertEquals("a", ExprUnparser.exprToString(proj.get(0).getSource()));
        assertFalse(proj.get(0).hasTargetAlias());
        assertEquals("b", ExprUnparser.exprToString(proj.get(1).getSource()));
        assertTrue(proj.get(1).hasTargetAlias());
        assertEquals("c", proj.get(1).getTargetAlias());
    }

    @Test
    public void testComplexTableSelectProjection() {
        String projectionString = "(1 + 1) * 100 as `one-o-two`, 'a is \\'a\\'' as `what is 'a'`";
        List<Projection> proj = new ExprParser(projectionString).parseTableSelectProjection();
        assertEquals(2, proj.size());

        assertEquals("((1 + 1) * 100)", ExprUnparser.exprToString(proj.get(0).getSource()));
        assertEquals("one-o-two", proj.get(0).getTargetAlias());

        assertEquals("a is 'a'", proj.get(1).getSource().getConstant().getScalar().getVString().getValue().toStringUtf8());
        assertEquals("what is 'a'", proj.get(1).getTargetAlias());
    }

    @Test
    public void testParseUpdateList() {
        String updateList = "a = 1*40, b = c, x@.b = 400, y = z == 1";
        List<UpdateOperation> ops = new ExprParser(updateList).parseUpdateList();
        assertEquals(4, ops.size());
        UpdateOperation op = ops.get(0);
        assertEquals("a", op.getSource().getName());
        assertEquals("(1 * 40)", ExprUnparser.exprToString(op.getValue()));
        op = ops.get(1);
        assertEquals("b", op.getSource().getName());
        assertEquals("c", op.getValue().getIdentifier().getName());
        op = ops.get(2);
        assertEquals("x", op.getSource().getName());
        assertEquals(1, op.getSource().getDocumentPathCount());
        assertEquals("b", op.getSource().getDocumentPath(0).getValue());
        assertEquals(400, op.getValue().getConstant().getScalar().getVSignedInt());
        op = ops.get(3);
        assertEquals("y", op.getSource().getName());
        assertEquals("(z == 1)", ExprUnparser.exprToString(op.getValue()));
    }
}