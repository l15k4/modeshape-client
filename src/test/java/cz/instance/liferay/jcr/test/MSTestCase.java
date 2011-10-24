package cz.instance.liferay.jcr.test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.security.auth.login.LoginContext;

import org.modeshape.jcr.JcrEngine;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.log4testng.Logger;

import cz.instance.liferay.jcr.RepositoryClient;
import cz.instance.liferay.jcr.test.ms.PersistOrderTest;
import cz.instance.utils.docs.Document;
import cz.instance.utils.docs.DocumentProvider;

public class MSTestCase {
	Logger log = Logger.getLogger(PersistOrderTest.class);
	
	public static final String superId = "jsmith";
	public static final String password = "secret";
	public RepositoryClient repositoryClient;
	public LoginContext loginContext;
	public Document[] docs;

	@BeforeSuite
	public void prepare() throws Exception {
		Document docEN = DocumentProvider.getDocByTypeAndLang("ppt", "en");
		Document docDE = DocumentProvider.getDocByTypeAndLang("ppt", "de");
		docs = new Document[] { docEN, docDE };
		URL config = MSTestCase.class.getClassLoader().getResource("config/repo-simple.xml");
		URL jaasConfig = MSTestCase.class.getClassLoader().getResource("security/jaas.conf.xml");
		repositoryClient = new RepositoryClient(config, jaasConfig, "modeshape-jcr");
		loginContext = repositoryClient.login(superId, password);
	}
	
	protected List<String> getNamesOfRepositories(JcrEngine engine) {
		List<String> names = new ArrayList<String>(engine.getRepositoryNames());
		Collections.sort(names);
		return Collections.unmodifiableList(names);
	}

	protected void waitUntilSequencedNodesIs(int totalNumberOfNodesSequenced) throws InterruptedException {
		// check 50 times, waiting 0.1 seconds between (for a total of 5 seconds max) ...
		long numFound = 0;
		for (int i = 0; i != 50; i++) {
			numFound = repositoryClient.getStatistics().getNumberOfNodesSequenced();
			if (numFound >= totalNumberOfNodesSequenced) {
				// Wait for the sequenced output to be saved before searching ...
				Thread.sleep(500);
				return;
			}
			Thread.sleep(100);
		}
		Assert.fail("Expected to find " + totalNumberOfNodesSequenced + " nodes sequenced, but found " + numFound);
	}

}
