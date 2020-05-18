package br.com.s1mbi0se.alluxio.underfs.b2;

import com.backblaze.b2.client.structures.B2Capabilities;

import java.util.Arrays;
import java.util.List;

public final class B2Utils
{
    public static short translateBucketAcl(List<String> capabilities)
    {
        short mode = (short) 0;

        if (capabilities.containsAll(Arrays.asList(B2Capabilities.READ_FILES, B2Capabilities.WRITE_FILES))) {
            // If the user has full control to the bucket, +rwx to the owner mode.
            mode |= (short) 0700;
        }
        else if (capabilities.contains(B2Capabilities.WRITE_FILES)) {
            // If the bucket is writable by the user, +w to the owner mode.
            mode |= (short) 0200;
        }
        else if (capabilities.contains(B2Capabilities.READ_FILES)) {
            // If the bucket is readable by the user, add r and x to the owner mode.
            mode |= (short) 0500;
        }

        return mode;
    }

    private B2Utils() {}
}
