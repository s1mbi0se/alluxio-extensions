/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package br.com.s1mbi0se.alluxio.underfs.b2;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.contentHandlers.B2ContentMemoryWriter;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.google.protobuf.ServiceException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link B2InputStream}.
 */
public class B2InputStreamTest
{

    private static final String BUCKET_NAME = "testBucket";
    private static final String OBJECT_KEY = "testObjectKey";

    @Mock
    private B2ContentMemoryWriter b2ContentMemoryWriter;
    private B2InputStream b2InputStream;
    private B2StorageClient b2StorageClient;
    private byte[] bytes;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp()
            throws ServiceException
    {
        MockitoAnnotations.initMocks(this);

        bytes = new byte[] {1, 2, 3};
        b2StorageClient = mock(B2StorageClient.class);
        b2ContentMemoryWriter = mock(B2ContentMemoryWriter.class);

        when(b2ContentMemoryWriter.getBytes()).thenReturn(bytes);

        b2InputStream = new B2InputStream(BUCKET_NAME, OBJECT_KEY, b2StorageClient, 0L, 100L)
        {
            @Override
            protected B2ContentMemoryWriter getSink()
            {
                return b2ContentMemoryWriter;
            }
        };
    }

    /**
     * Test of close method, of class S3InputStream.
     */
    @Test
    public void close()
            throws IOException
    {
        b2InputStream.close();

        expectedException.expect(IOException.class);
        expectedException.expectMessage(is("Stream closed"));
        b2InputStream.read();
    }

    /**
     * Test of read method, of class S3InputStream.
     */
    @Test
    public void read()
            throws IOException, B2Exception, ServiceException
    {

        assertEquals(1, b2InputStream.read());
        assertEquals(2, b2InputStream.read());
        assertEquals(3, b2InputStream.read());
    }

    /**
     * Test of read method, of class S3InputStream.
     */
    @Test
    public void readWithArgs()
            throws IOException
    {
        byte[] bytes = new byte[3];
        int readCount = b2InputStream.read(bytes, 0, 3);
        assertEquals(3, readCount);
        assertArrayEquals(new byte[] {1, 2, 3}, bytes);
    }

    /**
     * Test of skip method, of class S3InputStream.
     */
    @Test
    public void skip()
            throws IOException
    {
        assertEquals(1, b2InputStream.read());
        b2InputStream.skip(1);
        assertEquals(3, b2InputStream.read());
    }
}
