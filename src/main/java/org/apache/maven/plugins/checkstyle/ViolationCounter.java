package org.apache.maven.plugins.checkstyle;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Counts (and optionally logs) the Checkstyle violations of an XML report.
 * Extracted from {@link CheckstyleViolationCheckMojo}.
 */
class ViolationCounter
{
    private final Log mojoLog;

    private final String violationIgnore;

    private final String violationSeverity;

    private final boolean logViolationsToConsole;

    private final String basedir;

    ViolationCounter( Log mojoLog, String violationIgnore, String violationSeverity,
                      boolean logViolationsToConsole, String basedir )
    {
        this.mojoLog = mojoLog;
        this.violationIgnore = violationIgnore;
        this.violationSeverity = violationSeverity;
        this.logViolationsToConsole = logViolationsToConsole;
        this.basedir = basedir;
    }

    int countViolations( XmlPullParser xpp )
        throws XmlPullParserException, IOException
    {
        int count = 0;
        int ignoreCount = 0;
        List<RuleUtil.Matcher> ignores = violationIgnore == null ? Collections.<RuleUtil.Matcher>emptyList()
                        : RuleUtil.parseMatchers( violationIgnore.split( "," ) );

        String file = "";
        for ( int eventType = xpp.getEventType(); eventType != XmlPullParser.END_DOCUMENT; eventType = xpp.next() )
        {
            if ( eventType != XmlPullParser.START_TAG )
            {
                continue;
            }
            else if ( "file".equals( xpp.getName() ) )
            {
                file = PathTool.getRelativeFilePath( basedir, xpp.getAttributeValue( "", "name" ) );
                //file = file.substring( file.lastIndexOf( File.separatorChar ) + 1 );
            }
            else if ( "error".equals( xpp.getName() ) )
            {
                String severity = xpp.getAttributeValue( "", "severity" );

                if ( !isViolation( severity ) )
                {
                    continue;
                }

                String source = xpp.getAttributeValue( "", "source" );

                if ( ignore( ignores, source ) )
                {
                    ignoreCount++;
                }
                else
                {
                    count++;

                    if ( logViolationsToConsole )
                    {
                        String line = xpp.getAttributeValue( "", "line" );
                        String column = xpp.getAttributeValue( "", "column" );
                        String message = xpp.getAttributeValue( "", "message" );
                        String rule = RuleUtil.getName( source );
                        String category = RuleUtil.getCategory( source );

                        log( severity, file + ":[" + line + ( ( column == null ) ? "" : ( ',' + column ) ) + "] ("
                            + category + ") " + rule + ": " + message );
                    }
                }
            }
        }

        if ( ignoreCount > 0 )
        {
            mojoLog.info( "Ignored " + ignoreCount + " error" + ( ( ignoreCount > 1 ) ? "s" : "" ) + ", " + count
                               + " violation" + ( ( count > 1 ) ? "s" : "" ) + " remaining." );
        }

        return count;
    }

    private void log( String severity, String message )
    {
        if ( "info".equals( severity ) )
        {
            mojoLog.info( message );
        }
        else if ( "warning".equals( severity ) )
        {
            mojoLog.warn( message );
        }
        else
        {
            mojoLog.error( message );
        }
    }

    /**
     * Checks if the given severity is considered a violation.
     *
     * @param severity The severity to check
     * @return <code>true</code> if the given severity is a violation, otherwise <code>false</code>
     */
    private boolean isViolation( String severity )
    {
        if ( "error".equals( severity ) )
        {
            return "error".equals( violationSeverity ) || "warning".equals( violationSeverity )
                || "info".equals( violationSeverity );
        }
        else if ( "warning".equals( severity ) )
        {
            return "warning".equals( violationSeverity ) || "info".equals( violationSeverity );
        }
        else if ( "info".equals( severity ) )
        {
            return "info".equals( violationSeverity );
        }
        else
        {
            return false;
        }
    }

    private boolean ignore( List<RuleUtil.Matcher> ignores, String source )
    {
        for ( RuleUtil.Matcher ignore : ignores )
        {
            if ( ignore.match( source ) )
            {
                return true;
            }
        }
        return false;
    }
}
