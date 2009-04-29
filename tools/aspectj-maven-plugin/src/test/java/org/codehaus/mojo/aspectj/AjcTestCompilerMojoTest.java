package org.codehaus.mojo.aspectj;

import java.util.Collections;

/**
 * The MIT License
 *
 * Copyright 2005-2006 The Codehaus.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * Plugin testcases.
 * 
 * @author <a href="mailto:kaare.nilsen@gmail.com">Kaare Nilsen</a>
 *
 */
public class AjcTestCompilerMojoTest
    extends CompilerMojoTestBase
{
    
    /**
     * 
     */
    protected void setUp()
        throws Exception
    {
        ajcMojo = new AjcTestCompileMojo();
        super.setUp();
    }

    /**
     * @throws Exception
     */
    public void testUsingCorrectClasspath()
        throws Exception
    {
        try
        {
            ajcMojo.ajdtBuildDefFile = "test-build-1-5.ajproperties";
            ajcMojo.setComplianceLevel( "1.5" );
            ajcMojo.setVerbose( true );
            ajcMojo.setShowWeaveInfo( true );
            assertTrue(AjcHelper.createClassPath(ajcMojo.project, Collections.EMPTY_LIST, ajcMojo.getOutputDirectories()).indexOf("junit") != -1);
            ajcMojo.execute();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            fail( "Exception : " + e.toString() );
        }
    }

    String getProjectName()
    {
        return "test-project";
    }
}
