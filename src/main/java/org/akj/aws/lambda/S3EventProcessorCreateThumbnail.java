package org.akj.aws.lambda;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;

import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

public class S3EventProcessorCreateThumbnail extends RequestHandler2 {
	private static final float MAX_WIDTH = 200;
	private static final float MAX_HEIGHT = 200;
	private final String JPG_TYPE = (String) "jpg";
	private final String JPG_MIME = (String) "image/jpeg";
	private final String PNG_TYPE = (String) "png";
	private final String PNG_MIME = (String) "image/png";
	// private final String dstBucket = "tech-s3-demo";

	// private static Logger logger =
	// LoggerFactory.getLogger(S3EventProcessorCreateThumbnail.class);
	private static Logger logger = Logger.getLogger(S3EventProcessorCreateThumbnail.class);

	public String handleRequest(S3Event s3event, Context context) {
		try {
			S3EventNotificationRecord record = s3event.getRecords().get(0);

			String srcBucket = record.getS3().getBucket().getName();

			// Object key may have spaces or unicode non-ASCII characters.
			String srcKey = record.getS3().getObject().getKey().replace('+', ' ');
			srcKey = URLDecoder.decode(srcKey, "UTF-8");

			String dstBucket = srcBucket;
			String dstKey = srcKey;

			// Infer the image type.
			Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(srcKey);
			if (!matcher.matches()) {
				logger.error("Unable to infer image type for key " + srcKey);
				return "Unable to infer image type for key " + srcKey;
			}

			String imageType = matcher.group(1);
			if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType))) {
				logger.error("Skipping non-image " + srcKey);
				return "Skipping non-image " + srcKey;
			}

			// Download the image from S3 into a stream
			AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
			S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
			InputStream objectData = s3Object.getObjectContent();

			// Read the source image
			BufferedImage srcImage = ImageIO.read(objectData);
			int srcHeight = srcImage.getHeight();
			int srcWidth = srcImage.getWidth();
			// Infer the scaling factor to avoid stretching the image
			// unnaturally
			float scalingFactor = Math.min(MAX_WIDTH / srcWidth, MAX_HEIGHT / srcHeight);
			int width = (int) (scalingFactor * srcWidth);
			int height = (int) (scalingFactor * srcHeight);

			BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = resizedImage.createGraphics();
			// Fill with white before applying semi-transparent (alpha) images
			g.setPaint(Color.white);
			g.fillRect(0, 0, width, height);
			// Simple bilinear resize
			// If you want higher quality algorithms, check this link:
			// https://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(srcImage, 0, 0, width, height, null);
			g.dispose();

			// Re-encode image to target format
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			ImageIO.write(resizedImage, imageType, os);
			InputStream is = new ByteArrayInputStream(os.toByteArray());
			// Set Content-Length and Content-Type
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(os.size());
			if (JPG_TYPE.equals(imageType)) {
				meta.setContentType(JPG_MIME);
			}
			if (PNG_TYPE.equals(imageType)) {
				meta.setContentType(PNG_MIME);
			}

			// Uploading to S3 destination bucket
			dstKey = composeDstKey(srcKey);
			logger.info("Writing to: " + dstBucket + "/" + dstKey);
			s3Client.putObject(dstBucket, dstKey, is, meta);
			logger.info("Successfully resized " + srcBucket + "/" + srcKey + " and uploaded to " + dstBucket + "/"
					+ dstKey);
			return "success";
		} catch (IOException e) {
			logger.error(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	public String composeDstKey(String srcKey) {
		StringBuilder sbuilder = new StringBuilder();
		sbuilder.append(srcKey.substring(0, srcKey.lastIndexOf(File.separatorChar)) + "-resized" + File.separatorChar)
				.append(srcKey.substring(srcKey.lastIndexOf(File.separatorChar) + 1, srcKey.lastIndexOf(".")))
				.append("-" + (int) MAX_WIDTH + "@" + (int) MAX_HEIGHT)
				.append(srcKey.substring(srcKey.lastIndexOf('.')));

		return sbuilder.toString();
	}
}
