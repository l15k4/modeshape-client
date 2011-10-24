package cz.instance.liferay.jcr;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.modeshape.common.collection.Problem;
import org.modeshape.graph.ExecutionContext;
import org.modeshape.graph.Graph;
import org.modeshape.graph.JaasSecurityContext;
import org.modeshape.graph.Location;
import org.modeshape.graph.property.PathNotFoundException;
import org.modeshape.graph.property.Property;
import org.modeshape.jcr.JcrConfiguration;
import org.modeshape.jcr.JcrEngine;
import org.modeshape.jcr.JcrRepository;
import org.modeshape.jcr.JcrTools;
import org.modeshape.jcr.api.JaasCredentials;
import org.modeshape.repository.sequencer.SequencingService;
import org.picketbox.config.PicketBoxConfiguration;
import org.picketbox.factories.SecurityFactory;
import org.xml.sax.SAXException;

public class RepositoryClient {

	public static String jaasContextName;
	public JcrEngine engine;

	public RepositoryClient(URL config, URL securityConfig, String jaasContext) throws IOException, SAXException {
		engine = new JcrConfiguration().loadFrom(config).build();
		jaasContextName = jaasContext;
		SecurityFactory.prepare();
		try {
			PicketBoxConfiguration idtrustConfig = new PicketBoxConfiguration();
			idtrustConfig.load(securityConfig.openStream());
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	public void startEngine() {
		engine.start();
		if (engine.getProblems().hasProblems()) {
			for (Problem problem : engine.getProblems()) {
				System.err.println(problem.getMessageString());
			}
			throw new RuntimeException("Could not start due to problems");
		}
	}

	public void shutdownEngine() throws InterruptedException {
		this.engine.shutdown();
		this.engine.awaitTermination(4, TimeUnit.SECONDS);
		SecurityFactory.release();
	}

	public JcrRepository getRepository(String name) throws RepositoryException {
		return engine.getRepository(name);
	}

	public LoginContext login(String userId, String passwd) throws LoginException {
		LoginContext lc = new LoginContext(jaasContextName, getCallbackHandler(userId, passwd));
		lc.login(); // This authenticates the user
		return lc;
	}

	private CallbackHandler getCallbackHandler(String userId, String passwd) {
		return new JaasSecurityContext.UserPasswordCallbackHandler(userId, passwd.toCharArray());
	}

	public Session getSession(String repositoryName, LoginContext loginContext) throws RepositoryException, LoginException {
		JcrRepository repo = getRepository(repositoryName);
		return repo.login(new JaasCredentials(loginContext));
	}

	protected ExecutionContext getExecutionContext(LoginContext loginContext) throws LoginException {
		ExecutionContext context = engine.getExecutionContext();
		if (loginContext != null) {
			JaasSecurityContext security = new JaasSecurityContext(loginContext);
			context = context.with(security);
		}
		return context;
	}

	public void store(Session session, URL url, String mimeType, String nodePath, String filename) throws RepositoryException, IOException {

		if (mimeType == null) {
			System.err.println("Could not determine mime type for file " + url + ".  Cancelling upload.");
			return;
		}

		JcrTools tools = new JcrTools();
		try {
			// Create the node at the supplied path ...
			Node node = tools.findOrCreateNode(session, nodePath, "nt:folder", "nt:file");

			// Upload the file to that node ...
			Node contentNode = tools.findOrCreateChild(node, "jcr:content", "nt:resource");
			contentNode.setProperty("jcr:mimeType", mimeType);
			contentNode.setProperty("jcr:lastModified", Calendar.getInstance());
			Binary binaryValue = session.getValueFactory().createBinary(url.openStream());
			contentNode.setProperty("jcr:data", binaryValue);

			// Save the session ...
			session.save();
		} finally {
			session.logout();
		}

	}

	public SequencingService.Statistics getStatistics() {
		return engine.getSequencingService().getStatistics();
	}

	public void populateRepositorySources(String fromfolder, String forSource, String destNode) throws IOException, SAXException, URISyntaxException {
		URL location = Thread.currentThread().getContextClassLoader().getResource(fromfolder);
		engine.getGraph(forSource).importXmlFrom(location.toURI()).into(destNode);
	}

	public boolean getNodeInfo(LoginContext lc, String sourceName, String pathToNode, Map<String, Object[]> props, List<String> children)
			throws LoginException {
		ExecutionContext context = getExecutionContext(lc);
		try {
			Graph graph = engine.getGraph(context, sourceName);
			org.modeshape.graph.Node node = graph.getNodeAt(pathToNode);
			if (props != null) {
				for (Property property : node.getProperties()) {
					String name = property.getName().getString(context.getNamespaceRegistry());
					props.put(name, property.getValuesAsArray());
				}
			}
			if (children != null) {
				for (Location child : node.getChildren()) {
					String name = child.getPath().getLastSegment().getString(context.getNamespaceRegistry());
					children.add(name);
				}
			}
		} catch (PathNotFoundException e) {
			return false;
		}
		return true;
	}
}
