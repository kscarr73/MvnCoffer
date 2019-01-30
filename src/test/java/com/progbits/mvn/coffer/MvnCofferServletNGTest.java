/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.progbits.mvn.coffer;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 *
 * @author scarr
 */
public class MvnCofferServletNGTest {

    public MvnCofferServletNGTest() {
    }

    @Test
    public void testSomeMethod() throws Exception {
        ScriptEngineManager factory = new ScriptEngineManager();

        ScriptEngine engine = factory.getEngineByName("JavaScript");

        engine.put("func", new ScriptFunctions());

        for (int x = 0; x <= 10000; x++) {
            engine.eval("var sTemp = \"This is a test\";\n"
                    + "var nTemp = 10;\n"
                    + "func.runSomething(sTemp, nTemp);");
        }
    }

}
