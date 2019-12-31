/**
 * 
 * MIT License

Copyright (c) 2019 Infostretch Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.qmetry.qaf.automation.openapi.v3;

import static com.qmetry.qaf.automation.core.ConfigurationManager.getBundle;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

/**
 * This class can be run through command line to generate request call
 * repository and BDD source from OpenAPI specification. Set URL of the
 * specification document using property "openapi.specUrl" or command line
 * argument.
 * 
 * @author chirag.jayswal
 *
 */
public class CodeGeneratorCLI {

	/**
	 * @param args
	 * @throws IOException
	 * @throws ConfigurationException
	 */
	public static void main(String... args) throws ConfigurationException, IOException {

		if (args != null && args.length > 0) {
			getBundle().clearProperty("openapi.specUrl");
			for (String arg : args) {
				// ensure it is not command line options
				if (!arg.startsWith("-")) {
					getBundle().addProperty("openapi.specUrl", arg);
				}
			}
		}

		for (String specUrl : getBundle().getStringArray("openapi.specUrl")) {
			ParseOptions options = new ParseOptions();
			options.setResolve(true);
			options.setResolveFully(true);
			OpenAPI api = new OpenAPIV3Parser().read(specUrl, null, options);
			CodeGenerator codeGenerator = new CodeGenerator(specUrl, api);
			codeGenerator.generate();
		}
	}
}
