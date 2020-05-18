package br.com.s1mbi0se.alluxio.underfs.b2;

import alluxio.conf.PropertyKey;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class B2PropertyKey
{
    public static final PropertyKey B2_ACCESS_KEY = PropertyKey.fromString(B2PropertyKey.Name.B2_ACCESS_KEY);
    public static final PropertyKey B2_SECRET_KEY = PropertyKey.fromString(B2PropertyKey.Name.B2_SECRET_KEY);

    @ThreadSafe
    public static final class Name
    {
        public static final String B2_ACCESS_KEY = "b2AccessKeyId";
        public static final String B2_SECRET_KEY = "b2SecretKey";
    }
}
