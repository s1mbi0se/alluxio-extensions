package br.com.s1mbi0se.alluxio.underfs.b2;

import alluxio.AlluxioURI;
import alluxio.conf.PropertyKey;
import alluxio.underfs.UnderFileSystem;
import alluxio.underfs.UnderFileSystemConfiguration;
import alluxio.underfs.UnderFileSystemFactory;
import com.google.common.base.Preconditions;
import com.google.protobuf.ServiceException;

import javax.annotation.concurrent.ThreadSafe;

import java.io.IOException;

@ThreadSafe
public class B2UnderFileSystemFactory
        implements UnderFileSystemFactory
{
    @Override
    public UnderFileSystem create(String path, UnderFileSystemConfiguration conf)
    {
        Preconditions.checkNotNull(path, "path is null");
        if (checkB2Credentials(conf)) {
            try {

                return B2UnderFileSystem.createInstance(new AlluxioURI(path), conf);
            }
            catch (ServiceException e) {
                throw new RuntimeException(e);
            }
        }

        String err = "B2 Credentials not available, cannot create B2 Under File System.";
        throw new RuntimeException(new IOException(err));
    }

    private boolean checkB2Credentials(UnderFileSystemConfiguration conf)
    {
        return conf.keySet().contains(B2PropertyKey.B2_ACCESS_KEY) && conf
                .keySet().contains(B2PropertyKey.B2_SECRET_KEY);
    }

    @Override
    public boolean supportsPath(String path)
    {
        if (path == null) {
            return false;
        }
        return path.startsWith(B2UnderFileSystem.B2_SCHEME);
    }

    @Override
    public boolean supportsPath(String path, UnderFileSystemConfiguration conf)
    {
        return supportsPath(path);
    }
}
