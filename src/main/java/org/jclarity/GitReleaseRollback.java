package org.jclarity;

/*
 * Copyright 2013 John Oliver.
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

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * @author John Oliver
 * @goal rollback
 */
public class GitReleaseRollback extends AbstractMojo {

	/**
	 * The Maven Project Object
	 * 
	 * @parameter property="project"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * The Maven Session Object
	 * 
	 * @parameter property="session"
	 * @required
	 * @readonly
	 */
	private MavenSession session;

	/**
	 * The Maven PluginManager component.
	 * 
	 * @component
	 * @required
	 */
	private BuildPluginManager pluginManager;
	

	/**
	 * The Maven Session Object
	 * 
	 * @parameter default-value="2.4"
	 */
	private String releasePluginVersion;

	private File baseDir;


	public void execute() throws MojoExecutionException {
		baseDir = project.getBasedir();

		String scmTag = getScmTag();
		deleteTag(scmTag);
		executeReleaseRollback();
	}

	private void executeReleaseRollback() throws MojoExecutionException {
		executeMojo(
				plugin( groupId("org.apache.maven.plugins"),
						artifactId("maven-release-plugin"),
						version(releasePluginVersion)),
				goal("rollback"), 
				configuration(),
				executionEnvironment(project, session, pluginManager));
	}

	private void deleteTag(String scmTag) {
		try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();

			FileRepository db = builder .findGitDir(baseDir)
										.readEnvironment()
										.findGitDir()
										.build();

			Git git = new Git(db);

			List<String> result = git.tagDelete().setTags(scmTag).call();
			for (String tag : result) {
				git.push().add(":" + tag).call();
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to read git repo data", e);
		} catch (GitAPIException e) {
			throw new RuntimeException("Failed to remove tag", e);
		}
	}

	private String getScmTag() {
		File releasePropertiesFile = new File(baseDir, "release.properties");

		try {
			FileInputStream inStream = new FileInputStream(releasePropertiesFile);
			Properties releaseProperties = new Properties();
			releaseProperties.load(inStream);
			return releaseProperties.getProperty("scm.tag");
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Release properties file could not be found", e);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read release properties file", e);
		}
	}
}
