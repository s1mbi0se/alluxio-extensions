package br.com.s1mbi0se.alluxio.underfs.b2;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.contentHandlers.B2ContentMemoryWriter;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2DownloadByNameRequest;
import com.backblaze.b2.util.B2ByteRange;
import com.google.common.base.Throwables;
import com.google.protobuf.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@NotThreadSafe
public class B2InputStream
        extends InputStream
{
    private static final Logger LOG = LoggerFactory.getLogger(B2InputStream.class);
    private final String bucketName;
    private final String key;
    private final B2StorageClient b2StorageClient;
    private long offset;
    private long length;
    private BufferedInputStream bufferedInputStream;

    public B2InputStream(String bucketName, String key, B2StorageClient b2StorageClient, long length)
            throws ServiceException
    {
        this(bucketName, key, b2StorageClient, 0L, length);
    }

    public B2InputStream(String bucketName, String key, B2StorageClient b2StorageClient, long offset, long length)
            throws ServiceException
    {
        LOG.debug("B2InputStream");
        LOG.debug("bucket name [{}]", bucketName);
        LOG.debug("key [{}]", key);
        LOG.debug("offset [{}]", offset);
        LOG.debug("lenght [{}]", length);

        this.bucketName = bucketName;
        this.key = key;
        this.b2StorageClient = b2StorageClient;
        this.offset = offset;
        this.length = length;

        try {
            final B2ContentMemoryWriter sink = getSink();
            if (offset > 0) {
                final B2DownloadByNameRequest request = B2DownloadByNameRequest
                        .builder(this.bucketName, this.key)
                        .setRange(B2ByteRange.between(this.offset, this.length))
                        .build();
                this.b2StorageClient.downloadByName(request, sink);
            }
            else {
                final B2DownloadByNameRequest request = B2DownloadByNameRequest
                        .builder(this.bucketName, this.key)
                        .build();
                this.b2StorageClient.downloadByName(request, sink);
            }
            bufferedInputStream = new BufferedInputStream(new ByteArrayInputStream(sink.getBytes()));
        }
        catch (B2Exception e) {
            Throwables.propagateIfPossible(e, ServiceException.class);
        }
    }

    protected B2ContentMemoryWriter getSink()
    {
        return B2ContentMemoryWriter.build();
    }

    @Override
    public int read()
            throws IOException
    {
        LOG.debug("B2InputStream read");
        int ret = bufferedInputStream.read();
        if (ret != -1) {
            offset++;
        }
        return ret;
    }

    @Override
    public void close()
            throws IOException
    {
        LOG.debug("B2InputStream close");
        bufferedInputStream.close();
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException
    {
        LOG.debug("B2InputStream read byte array");
        int ret = bufferedInputStream.read(b, off, len);
        if (ret != -1) {
            offset += ret;
        }
        return ret;
    }

    /**
     * This method leverages the ability to open a stream from S3 from a given offset. When the
     * underlying stream has fewer bytes buffered than the skip request, the stream is closed, and
     * a new stream is opened starting at the requested offset.
     *
     * @param n number of bytes to skip
     * @return the number of bytes skipped
     */
    @Override
    public long skip(long n)
            throws IOException
    {
        LOG.debug("B2InputStream skip");
        if (bufferedInputStream.available() >= n) {
            return bufferedInputStream.skip(n);
        }
        // The number of bytes to skip is possibly large, open a new stream from S3.
        bufferedInputStream.close();
        offset += n;
        try {
            final B2ContentMemoryWriter sink = getSink();
            final B2DownloadByNameRequest request = B2DownloadByNameRequest
                    .builder(this.bucketName, this.key)
                    .setRange(B2ByteRange.between(this.offset, this.length))
                    .build();
            this.b2StorageClient.downloadByName(request, sink);
            bufferedInputStream = new BufferedInputStream(new ByteArrayInputStream(sink.getBytes()));
        }
        catch (B2Exception e) {
            throw new IOException(e);
        }
        return n;
    }
}
