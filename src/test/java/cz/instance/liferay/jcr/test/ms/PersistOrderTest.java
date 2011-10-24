package cz.instance.liferay.jcr.test.ms;

import java.io.File;
import java.io.FileInputStream;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.modeshape.jcr.JcrTools;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.log4testng.Logger;

import cz.instance.liferay.jcr.test.MSTestCase;
import cz.instance.utils.docs.Document;

public class PersistOrderTest extends MSTestCase {
	Logger log = Logger.getLogger(PersistOrderTest.class);

	private static final String repositoryName = "inMemoryRepo";
	private static final String orderId = "123";
	private static final String orders = superId + "/" + "orders" + "/";

	@BeforeClass
	public void before() throws Exception {
		repositoryClient.startEngine();
	}

	@AfterClass
	public void after() throws Exception {
		repositoryClient.shutdownEngine();
	}

	@Test
	public void createNode() throws Exception {
		Session session = repositoryClient.getSession(repositoryName, loginContext);
		Node repositoryRoot = session.getRootNode();
		
		log.info("repositoryRoot : " + repositoryRoot.getPath());
		
		Node orderIDfolder = repositoryRoot.addNode(superId).addNode("orders").addNode(orderId, "nt:folder");
		orderIDfolder.addMixin("mix:created");
		
		log.info("orderIDfolder : " + orderIDfolder.getPath());
		
		Node file = orderIDfolder.addNode(docs[0].getFile().getName(), "nt:file");
		file.addMixin("transl:file");
		
		log.info("FilePath : " + file.getPath());
		
		Node[] contents = new Node[] { file.addNode("jcr:content", "nt:resource"), file.addNode("transl:translatedcontent", "nt:resource") };
		for (int i = 0; i < contents.length; i++) {
			setUpContent(session, contents[i], docs[i]);
		}
		session.save();
		JcrTools jcrTools = new JcrTools(true);
		jcrTools.printSubgraph(repositoryRoot, 4);
		//		waitUntilSequencedNodesIs(2);
		session.logout();
	}

	@Test
	public void testNodeAndPropertyPresence() throws Exception {
		Session session = repositoryClient.getSession(repositoryName, loginContext);
		Node repositoryRoot = session.getRootNode();
		Node orderIDfolder = repositoryRoot.getNode(orders + orderId);
		Node file = orderIDfolder.getNode(docs[0].getFile().getName());
		Node[] contents = new Node[] { file.getNode("jcr:content"), file.getNode("transl:translatedcontent") };
		for (int i = 0; i < contents.length; i++) {
			testContent(contents[i], docs[i]);
		}
		session.logout();
	}

	private void setUpContent(Session session, Node content, Document entity) throws Exception {
		Binary binary = session.getValueFactory().createBinary(new FileInputStream(entity.getFile()));
		content.addMixin("mix:created");
		content.addMixin("mix:title");
		content.addMixin("mix:language");
		content.addMixin("transl:content");
		content.setProperty("jcr:data", binary);
		content.setProperty("jcr:mimeType", entity.getMediaType().toString());
		content.setProperty("jcr:title", "title");
		content.setProperty("jcr:description", "description");
		content.setProperty("jcr:language", entity.getState());
		content.setProperty("transl:wordcount", entity.getWordCount());
		content.setProperty("transl:text", entity.getContent());
		content.setProperty("transl:size", entity.getSize());
		content.setProperty("transl:checksum", entity.getChecksum());
	}

	private void testContent(Node content, Document entity) throws Exception {
		File resultFile = new File(System.getProperty("java.io.tmpdir"), entity.getFile().getName());
		FileUtils.writeByteArrayToFile(resultFile, IOUtils.toByteArray(content.getProperty("jcr:data").getBinary().getStream()));
		Assert.assertEquals(entity.getSize(), resultFile.length());
		Assert.assertEquals(entity.getChecksum(), FileUtils.checksumCRC32(resultFile));
		Assert.assertEquals(content.getProperty("jcr:mimeType").getString(), entity.getMediaType().toString());
		Assert.assertEquals(content.getProperty("jcr:title").getString(), "title");
		Assert.assertEquals(content.getProperty("jcr:description").getString(), "description");
		Assert.assertEquals(content.getProperty("jcr:language").getString(), entity.getState());
		Assert.assertEquals(content.getProperty("transl:wordcount").getLong(), entity.getWordCount());
		Assert.assertEquals(content.getProperty("transl:text").getString(), entity.getContent());
		Assert.assertEquals(content.getProperty("transl:size").getLong(), entity.getSize());
		Assert.assertEquals(content.getProperty("transl:checksum").getLong(), entity.getChecksum());
	}
}
