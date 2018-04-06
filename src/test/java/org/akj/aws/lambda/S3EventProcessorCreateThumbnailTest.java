package org.akj.aws.lambda;

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

public class S3EventProcessorCreateThumbnailTest {

	private S3EventProcessorCreateThumbnail client;

	@Before
	public void setUp() throws Exception {
		client = new S3EventProcessorCreateThumbnail();
	}

	@Test
	public void test() {
		String result = client.composeDstKey("c:\\images\\test.jpg");
		Assert.assertEquals("c:\\images-resized\\test-200@200.jpg", result);
	}

}
