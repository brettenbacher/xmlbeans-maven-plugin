package org.codehaus.mojo.xmlbeans;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileFilter;
import java.util.Iterator;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.xmlbeans.impl.tool.SchemaCompiler;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.xml.sax.EntityResolver;

/**
 * <p>A Maven 2 plugin which parses xsd files and produces a corresponding object
 * model based on the Apache XML Beans parser.</p>
 *
 * <p>The plugin produces two sets of output files referred to as generated sources
 * and generated classes. The former is then compiled to the build
 * <code>outputDirectory</code>. The latter is generated in this directory.</p>
 *
 * <p>Note that the descriptions for the goal's parameters have been blatently
 * copied from http://xmlbeans.apache.org/docs/2.0.0/guide/antXmlbean.html for
 * convenience.</p>
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:kris.bravo@corridor-software.us">Kris Bravo</a>
 * @version $Id$
 * @goal xmlbeans
 * @phase generate-sources
 * @requiresDependencyResolution compile
 * @description Creates java beans which map to XML schemas.
 *
 */
public class XmlBeansPlugin
   extends AbstractMojo
   implements PluginProperties
{

   /**
    * A file that points to either an individual schema file or a directory of files.
    * Not a path reference.  If multiple schema files need to be built together,
    * use a nested fileset instead of setting schema.
    *
    * @parameter expression="${sourceSchemas}"
    * @parameter default-value="empty"
    * @optional
    */
   private String sourceSchemas;

   /**
    * The directory where .xsd files are to be found.
    *
    * @parameter expression="${schemaDirectory}"
    * @required
    */
   private File schemaDirectory;

   /**
    * Define the name of the jar file created.  For instance, "myXMLBean.jar"
    * will output the results of this task into a jar with the same name.
    *
    * @parameter expression="${outputJar}"
    * @optional
    */
   private File outputJar;

   /**
    * Set to true to permit the compiler to download URLs for imports and
    * includes.  Defaults to false, meaning all imports and includes must
    * be copied locally.
    *
    * @parameter expression="${download}" default-value="false"
    * @optional
    */
   private boolean download;

   /**
    * Set a location to generate CLASS files into.
    *
    * @parameter expression="${project.build.outputDirectory}"
    * @required
    * @readonly
    */
   private File classGenerationDirectory;

   /**
    * Set a location to generate JAVA files into.
    *
    * @parameter expression="${project.build.directory}/generated-source"
    * @required
    * @readonly
    */
   private File sourceGenerationDirectory;

   /**
    * Indicates whether source should be compiled with debug information;
    * defaults to off. If set to off, -g:none will be passed on the command
    * line for compilers that support it (for other compilers, no command
    * line argument will be used). If set to true, the value of the debug
    * level attribute determines the command line argument.
    *
    * @parameter expression="${maven.compiler.debug}"
    * @optional
    */
   private boolean debug;

   /**
    * The initial size of the memory for the underlying VM, if javac is run
    * externally; ignored otherwise. Defaults to the standard VM memory setting.
    * (Examples: 83886080, 81920k, or 80m)
    *
    * @parameter expression="${memoryInitialSize}"
    * @optional
    */
   private String memoryInitialSize;

   /**
    * The maximum size of the memory for the underlying VM, if javac is run
    * externally; ignored otherwise. Defaults to the standard VM memory setting.
    * (Examples: 83886080, 81920k, or 80m)
    *
    * @parameter expression="${memoryMaximumSize}"
    * @optional
    */
   private String memoryMaximumSize;

   /**
    * The compiler implementation to use. If this attribute is not set, the
    * value of the build.compiler property, if set, will be used. Otherwise,
    * the default compiler for the current VM will be used.
    *
    * @parameter expression="${compiler}"
    * @optional
    */
   private String compiler;

   /**
    * Controls the amount of build message output.
    *
    * @parameter expression="${verbose}" default-value="true"
    * @optional
    */
   private boolean verbose = true;

   /**
    * Controls the amount of build message output.
    *
    * @parameter expression="${quiet}" default-value="false"
    * @optional
    */
   private boolean quiet = false;

   /**
    * Do not enforce the unique particle attribution rule.
    *
    * @parameter expression="${noUpa}" default-value="false"
    * @optional
    */
   private boolean noUpa;

   /**
    * Do not enforce the particle valid (restriction) rule.
    *
    * @parameter expression="${noPvr}" default-value="false"
    * @optional
    */
   private boolean noPvr;

   /**
    * Todo: Unkown use.
    *
    * @parameter expression="${jaxb}" default-value="false"
    * @optional
    */
   private boolean jaxb;

   /**
    * Don't compile the generated source files.
    *
    * @parameter expression="${noJavac}" default-value="false"
    * @optional
    */
   private boolean noJavac;

   /**
    * Configuration files used by the object generator. For more information
    * about the format of these files, see Todo.
    *
    * @parameter expression="${xmlConfigs}"
    * @optional
    */
   private String xmlConfigs;

   /**
    * Todo: Unknown use.
    *
    * @parameter expression="${catalogLocation}"
    * @optional
    */
   private String catalogLocation;


   /**
    * The repository for libraries we depend on.
    *
    * @parameter expression="${localRepository}"
    * @required
    * @readonly
    */
   private ArtifactRepository localRepository;

   /**
    * A reference to the Maven Project metadata.
    *
    * @parameter expression="${project}"
    * @required
    * @readonly
    */
   private MavenProject project;

   /**
    * Used to find resources used by the XML compiler. Currently not passed
    * to the compiler, since everything is on the classpath.
    *
    */
   private EntityResolver entityResolver;

   /**
    * Empty constructor for the XML Beans plugin.
    */
   public XmlBeansPlugin() {
   }

   /**
    * <p>Map the parameters to the schema compilers parameter object, make
    * sure the necessary output directories exist, then call on the schema
    * compiler to produce the java objects and supporting resources.</p>
    *
    * @throws MojoExecutionException Errors occurred during compile.
    *
    */
   public final void execute()
      throws org.apache.maven.plugin.MojoExecutionException
   {

      try
      {
         SchemaCompiler.Parameters compilerParams = ParameterAdapter.getCompilerParameters(this);

         compilerParams.getSrcDir().mkdirs();

         boolean result = SchemaCompiler.compile(compilerParams);

         if (!result)
         {
            StringBuffer errors = new StringBuffer();
            for (Iterator iterator = compilerParams.getErrorListener().iterator(); iterator.hasNext();)
            {
               Object o = (Object) iterator.next();
               errors.append("xml Error" + o);
               errors.append("\n");
            }
            throw new XmlBeansException(XmlBeansException.COMPILE_ERRORS, errors.toString());
         }
         else
         {
            project.addCompileSourceRoot(compilerParams.getSrcDir().getAbsolutePath());
            project.getCompileClasspathElements().add(compilerParams.getClassesDir());
         }

      }
      catch (DependencyResolutionRequiredException drre)
      {
         throw new XmlBeansException(XmlBeansException.CLASSPATH_DEPENDENCY, drre);
      }
   }

   /**
    * Check the required parameters for values. Report an error if something
    * isn't quite right.
    *
    * @throws MojoExecutionException Validation failed.
    */
   private void validateParameters()
      throws org.apache.maven.plugin.MojoExecutionException
   {
      StringBuffer errors = new StringBuffer();

      // directory with schemas
      if (schemaDirectory == null)
      {
         throw new XmlBeansException(XmlBeansException.MISSING_SCHEMA_DIRECTORY);
      }

      if (project.getBuild() != null)
      {
         if (project.getBuild().getScriptSourceDirectory() == null)
         {
            // TODO. add to string buffer.
         }
      }
      else
      {
         // TODO. add to string buffer.
      }

      if (errors.length() > 0)
      {
         throw new XmlBeansException(XmlBeansException.UNSET_PROPERTIES, errors.toString());
      }
   }

   /**
    * Gives the plugin a reference to the local repository.
    *
    * @param repository The local repository.
    */
   public final void setLocalRepository(final ArtifactRepository repository)
   {
      localRepository = repository;
   }

   /**
    * Returns the directory where the schemas are located. Note that this is
    * the base directory of the schema compiler, not the maven project.
    *
    * @return The schema directory.
    */
   public final File getBaseDir()
   {
      return schemaDirectory;
   }

   /**
    * Returns a classpath for the compiler made up of artifacts from the project.
    *
    * @throws DependencyResolutionRequiredException Maven dependencies weren't set.
    *
    * @return Array of classpath entries.
    */
   public final File[] getClasspath()
      throws DependencyResolutionRequiredException
   {
      Set artifacts = project.getArtifacts();
      File[] results = null;
      if (artifacts != null)
      {
         results = new File[artifacts.size()];

         Artifact nextArtifact = null;
         int j = 0;
         for (Iterator i = artifacts.iterator(); i.hasNext(); )
         {
            nextArtifact = (Artifact) i.next();
            results[j] = nextArtifact.getFile();
            j++;
         }
      }
      else
      {
         results = new File[0];
      }

      return results;
   }

   /**
    * Returns null. Currently the compiler preference isn't passwed to the xml beans
    * compiler.
    *
    * @return null.
    */
   public final String getCompiler()
   {
      return null;
   }

   /**
    * Returns configuration files identified in the xmlConfigs string passed
    * by the project configuration.
    *
    * @return An array of configuration files.
    */
   public final File[] getConfigFiles()
   {

      if (xmlConfigs != null)
      {
         List configs = new ArrayList();
         for (StringTokenizer st = new StringTokenizer(xmlConfigs, ","); st.hasMoreTokens();)
         {
            String configName = st.nextToken();
            configs.add(new File(configName));
         }
         return (File[]) configs.toArray(new File[] {});
      }
      else
      {
         return null;
      }
   }

   /**
    * Returns a null entity resolver.
    *
    * @return entityResolver set to null.
    */
   public final EntityResolver getEntityResolver()
   {
      return entityResolver;
   }

   /**
    * Returns an empty collection the compiler will store error message Strings
    * in.
    *
    * @return An empty ArrayList.
    */
   public final Collection getErrorListeners()
   {
      Collection listener = new ArrayList();
      return listener;
   }

   /**
    * Todo: Not certain of the purpose of this.
    *
    * @return null at this time.
    */
   public final List getExtensions()
   {
      return null;
   }

   /**
    * Returns the class directory of the project.
    *
    * @return The project build classes directory.
    */
   public final File getGeneratedClassesDirectory()
   {
      return classGenerationDirectory;
   }

   /**
    * Returns the directory for saving generated source files.
    *
    * @return The generated=sources directory.
    */
   public final File getGeneratedSourceDirectory()
   {
      return sourceGenerationDirectory;
   }

   /**
    * An array of other source files. Currently an empty array.
    *
    * @return An empty file array.
    */
   public final File[] getJavaFiles()
   {
      return new File[] {};
   }

   /**
    * Returns null at this time. Passed to the schema compiler.
    *
    * @return null.
    */
   public final Set getMdefNamespaces()
   {
      return null;
   }

   /**
    * Returns the initial size of the memory allocation for the schema compile process.
    *
    * @return The initial memory size value.
    */
   public final String getMemoryInitialSize()
   {
      return memoryInitialSize;
   }

   /**
    * Returns the maximum size of the memory allocation for the schema compile process.
    *
    * @return The max memory size value.
    */
   public final String getMemoryMaximumSize()
   {
      return memoryMaximumSize;
   }

   /**
    * Returns null at this time. This is passed to the schema compiler.
    *
    * @return null.
    */
   public final String getName()
   {
      return null;
   }

   /**
    * Returns the location of the output jar file should one be produced.
    *
    * @return The jar file location.
    */
   public final File getOutputJar()
   {
      return outputJar;
   }

   /**
    * Todo: Not certain of the purpose of this.
    *
    * @return null at this time.
    */
   public final String getRepackage()
   {
      return null;
   }

   /**
    * Currently returns an empty file array.
    *
    * @return An empty file array.
    */
   public final File[] getWsdlFiles()
   {
      return new File[] {};
   }

   /**
    * Returns a file array of xsd files to translate to object models.
    *
    * @return An array of schema files to be parsed by the schema compiler.
    */
   public final File[] getXsdFiles()
   {
      List schemas = new ArrayList();
      if (sourceSchemas != null)
      {
         for (StringTokenizer st = new StringTokenizer(sourceSchemas, ","); st.hasMoreTokens();)
         {
            String schemaName = st.nextToken();
            schemas.add(new File(schemaDirectory, schemaName));
         }
      }
      else
      {
         File[] files = schemaDirectory.listFiles(new XSDFile());
         File nextFile = null;
         for (int i = 0; i < files.length; i++)
         {
            schemas.add(files[i]);
         }
      }
      return (File[]) schemas.toArray(new File[] {});
   }

   /**
    * Returns the state of debuggin.
    *
    * @return true if debug mode.
    */
   public final boolean isDebug()
   {
      return debug;
   }

   /**
    * Returns true if dependencies are to be downloaded by the schema compiler.
    *
    * @return true if resources should be downloaded.
    */
   public final boolean isDownload()
   {
      return download;
   }

   /**
    * Returns true if jaxb is set.
    *
    * @return true if the jaxb flag on the schema compiler should be set.
    */
   public final boolean isJaxb()
   {
      return jaxb;
   }

   /**
    * Returns True if generated source files are not to be compiled.
    *
    * @return true if no compiling should occur.
    */
   public final boolean isNoJavac()
   {
      return noJavac;
   }

   /**
    * Do not enforce the particle valid (restriction) rule if true.
    *
    * @return true if no enforcement should occur.
    */
   public final boolean isNoPvr()
   {
      return noPvr;
   }

   /**
    * If true, do not enforce the unique particle attribution rule.
    *
    * @return particle attibution enforcement
    */
   public final boolean isNoUpa()
   {
      return noUpa;
   }

   /**
    * Returns true if the schema compiler should reduce verbosity.
    *
    * @return true if message suppression is on.
    */
   public final boolean isQuiet()
   {
      return quiet;
   }

   /**
    * Returns true if the schema compiler should increase verbosity.
    *
    * @return true if verbose mode is on.
    */
   public final boolean isVerbose()
   {
      return verbose;
   }

   /**
    * No validation beyond those done by the maven plugin occur at this time.
    *
    * @throws XmlBeansException Currently not used.
    */
   public final void validate()
      throws XmlBeansException
   {
   }

   /**
    * A class used to look up .xsd documents from a given directory.
    *
    */
   private final class XSDFile
      implements FileFilter
   {

      /**
       * Returns true if the file ends with an xsd extension.
       *
       * @param file The filed being reviewed by the filter.
       *
       * @return true if an xsd file.
       */
      public boolean accept(final java.io.File file)
      {
         return file.getName().endsWith(".xsd");
      }

   }
}
