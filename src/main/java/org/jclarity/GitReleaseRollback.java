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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
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

	/**
	 * 
	 * @component
	 * @required
	 */
    private RepositorySystem repositorySystem;
    

	/**
	 * @component
	 * @required
	 */
	private MavenSession projectBuilderConfiguration;
    
	private File baseDir;

	private Properties releaseProperties;

	/**
	 * Whether or not to delete the deployed artifact
	 * 
	 * @parameter
	 */
	protected boolean deleteArtifact = true;

	/**
	 * Whether or not to delete the git tag created
	 * 
	 * @parameter
	 */
	private boolean deleteTag = true;

	/**
	 * Whether or not to perform maven rollback
	 * 
	 * @parameter
	 */
	protected boolean performRollBack = true;

	public void execute() throws MojoExecutionException {
		baseDir = project.getBasedir();
		releaseProperties = loadProperties();
		
		
		if(deleteTag)
			deleteTag();
		if(deleteArtifact)
			deleteDeployment();
		if(performRollBack)
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

	private void deleteTag() {
		try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();

			FileRepository db = builder .findGitDir(baseDir)
										.readEnvironment()
										.findGitDir()
										.build();

			Git git = new Git(db);

			String scmTag = releaseProperties.getProperty("scm.tag");
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
	
	protected void deleteDeployment() {
		String version = releaseProperties.getProperty("project.rel."+project.getGroupId()+":"+project.getArtifactId());
		
		ArtifactRepository releaseArtifactRepository = getReleaseRepo();
		
		if(releaseArtifactRepository == null) {
	        getLog().warn("Failed to find the release repo, any released artifacts will not be deleted");
			return;
		}
		
		String username = releaseArtifactRepository.getAuthentication().getUsername();
		String password = releaseArtifactRepository.getAuthentication().getPassword();

		String url = releaseArtifactRepository.getUrl()+"/"+project.getGroupId()+"/"+project.getArtifactId()+"/"+version;

		try {
			int resposeCode =   Executor.newInstance()
										.auth(username, password)
										.execute(Request.Delete(url))
										.returnResponse()
										.getStatusLine()
										.getStatusCode();
			
			if(resposeCode != HttpStatus.SC_NO_CONTENT) {
				getLog().warn("Could not delete artifact, it may not have been deployed");
			}
		} catch (Exception e) {
			getLog().warn("Failed to delete artifact");
		}
		
	}

	private ArtifactRepository getReleaseRepo() {
		DistributionManagement distributionManagement = project.getDistributionManagement();
        if ( distributionManagement != null && distributionManagement.getRepository() != null ) {        	
        	try {
        		ArtifactRepository repo = repositorySystem.buildArtifactRepository(distributionManagement.getRepository());

                repositorySystem.injectProxy( projectBuilderConfiguration.getRepositorySession(), Arrays.asList( repo ) );
                repositorySystem.injectAuthentication( projectBuilderConfiguration.getRepositorySession(),
                                                       Arrays.asList( repo ) );
                return repo;
        	} catch (InvalidRepositoryException e) {}
        }
        return null;
	}

	private Properties loadProperties() {
		File releasePropertiesFile = new File(baseDir, "release.properties");

		try {
			FileInputStream inStream = new FileInputStream(releasePropertiesFile);
			Properties properties = new Properties();
			properties.load(inStream);
			return properties;
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Release properties file could not be found", e);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read release properties file", e);
		}
	}
}
