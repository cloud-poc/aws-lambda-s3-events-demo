package org.akj.aws.lambda;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class S3EventProcessorCreateThumbnailTest {

	private S3EventProcessorCreateThumbnail client;
	
	@Before
	public void setUp() throws Exception {
		client = new S3EventProcessorCreateThumbnail();
	}

	@Test
	public void test() {
		System.out.println(client.composeDstKey("c:\\images\\test.jpg"));
	}

}
