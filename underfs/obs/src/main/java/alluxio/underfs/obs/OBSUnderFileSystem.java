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

package alluxio.underfs.obs;

import alluxio.AlluxioURI;
import alluxio.Constants;
import alluxio.conf.PropertyKey;
import alluxio.retry.RetryPolicy;
import alluxio.underfs.ObjectUnderFileSystem;
import alluxio.underfs.UnderFileSystem;
import alluxio.underfs.UnderFileSystemConfiguration;
import alluxio.underfs.options.OpenOptions;
import alluxio.util.UnderFileSystemUtils;
import alluxio.util.io.PathUtils;

import com.google.common.base.Preconditions;
import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.S3Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Huawei OBS {@link UnderFileSystem} implementation.
 */
@ThreadSafe
public class OBSUnderFileSystem extends ObjectUnderFileSystem {
  private static final Logger LOG = LoggerFactory.getLogger(OBSUnderFileSystem.class);

  /** Suffix for an empty file to flag it as a directory. */
  private static final String FOLDER_SUFFIX = PATH_SEPARATOR;

  /** Huawei OBS client. */
  private final ObsClient mClient;

  /** Bucket name of user's configured Alluxio bucket. */
  private final String mBucketName;

  /**
   * Constructs a new instance of {@link OBSUnderFileSystem}.
   *
   * @param uri the {@link AlluxioURI} for this UFS
   * @param conf the configuration for this UFS
   * @return the created {@link OBSUnderFileSystem} instance
   */
  public static OBSUnderFileSystem createInstance(AlluxioURI uri,
      UnderFileSystemConfiguration conf) throws Exception {
    Preconditions.checkArgument(conf.isSet(OBSPropertyKey.OBS_ACCESS_KEY),
        "Property %s is required to connect to OBS", OBSPropertyKey.OBS_ACCESS_KEY);
    Preconditions.checkArgument(conf.isSet(OBSPropertyKey.OBS_SECRET_KEY),
        "Property %s is required to connect to OBS", OBSPropertyKey.OBS_SECRET_KEY);
    Preconditions.checkArgument(conf.isSet(OBSPropertyKey.OBS_ENDPOINT),
        "Property %s is required to connect to OBS", OBSPropertyKey.OBS_ENDPOINT);
    String accessKey = conf.get(OBSPropertyKey.OBS_ACCESS_KEY);
    String secretKey = conf.get(OBSPropertyKey.OBS_SECRET_KEY);
    String endPoint = conf.get(OBSPropertyKey.OBS_ENDPOINT);

    ObsClient obsClient = new ObsClient(accessKey, secretKey, endPoint);
    String bucketName = UnderFileSystemUtils.getBucketName(uri);
    return new OBSUnderFileSystem(uri, obsClient, bucketName, conf);
  }

  /**
   * Constructor for {@link OBSUnderFileSystem}.
   *
   * @param uri the {@link AlluxioURI} for this UFS
   * @param obsClient Huawei OBS client
   * @param bucketName bucket name of user's configured Alluxio bucket
   * @param conf configuration for this UFS
   */
  protected OBSUnderFileSystem(AlluxioURI uri, ObsClient obsClient, String bucketName,
      UnderFileSystemConfiguration conf) {
    super(uri, conf);
    mClient = obsClient;
    mBucketName = bucketName;
  }

  @Override
  public void cleanup() {}

  @Override
  public String getUnderFSType() {
    return "obs";
  }

  // No ACL integration currently, no-op
  @Override
  public void setOwner(String path, String user, String group) {}

  // No ACL integration currently, no-op
  @Override
  public void setMode(String path, short mode) throws IOException {}

  @Override
  protected boolean copyObject(String src, String dst) {
    try {
      LOG.debug("Copying {} to {}", src, dst);
      mClient.copyObject(mBucketName, src, mBucketName, dst);
      return true;
    } catch (ObsException e) {
      LOG.error("Failed to rename file {} to {}", src, dst, e);
      return false;
    }
  }

  @Override
  public boolean createEmptyObject(String key) {
    try {
      ObjectMetadata objMeta = new ObjectMetadata();
      objMeta.setContentLength(0L);
      mClient.putObject(mBucketName, key, new ByteArrayInputStream(new byte[0]), objMeta);
      return true;
    } catch (ObsException e) {
      LOG.error("Failed to create object: {}", key, e);
      return false;
    }
  }

  @Override
  protected OutputStream createObject(String key) throws IOException {
    return new OBSOutputStream(mBucketName, key, mClient,
        mUfsConf.getList(PropertyKey.TMP_DIRS, ","));
  }

  @Override
  protected boolean deleteObject(String key) {
    try {
      mClient.deleteObject(mBucketName, key);
    } catch (ObsException e) {
      LOG.error("Failed to delete {}", key, e);
      return false;
    }
    return true;
  }

  @Override
  protected String getFolderSuffix() {
    return FOLDER_SUFFIX;
  }

  @Override
  protected ObjectListingChunk getObjectListingChunk(String key, boolean recursive)
      throws IOException {
    String delimiter = recursive ? "" : PATH_SEPARATOR;
    key = PathUtils.normalizePath(key, PATH_SEPARATOR);
    // In case key is root (empty string) do not normalize prefix
    key = key.equals(PATH_SEPARATOR) ? "" : key;
    ListObjectsRequest request = new ListObjectsRequest(mBucketName);
    request.setPrefix(key);
    request.setMaxKeys(getListingChunkLength(mUfsConf));
    request.setDelimiter(delimiter);

    ObjectListing result = getObjectListingChunk(request);
    if (result != null) {
      return new OBSObjectListingChunk(request, result);
    }
    return null;
  }

  // Get next chunk of listing result
  private ObjectListing getObjectListingChunk(ListObjectsRequest request) {
    ObjectListing result;
    try {
      result = mClient.listObjects(request);
    } catch (ObsException e) {
      LOG.error("Failed to list path {}", request.getPrefix(), e);
      result = null;
    }
    return result;
  }

  /**
   * Customized {@link ObjectListingChunk}.
   */
  private final class OBSObjectListingChunk implements ObjectListingChunk {
    final ListObjectsRequest mRequest;
    final ObjectListing mResult;

    OBSObjectListingChunk(ListObjectsRequest request, ObjectListing result) throws IOException {
      mRequest = request;
      mResult = result;
      if (mResult == null) {
        throw new IOException("OBS listing result is null");
      }
    }

    @Override
    public ObjectStatus[] getObjectStatuses() {
      List<S3Object> objects = mResult.getObjectSummaries();
      ObjectStatus[] ret = new ObjectStatus[objects.size()];
      int i = 0;
      for (S3Object obj : objects) {
        ret[i++] = new ObjectStatus(obj.getObjectKey(), obj.getMetadata().getEtag(),
            obj.getMetadata().getContentLength(), obj.getMetadata().getLastModified().getTime());
      }
      return ret;
    }

    @Override
    public String[] getCommonPrefixes() {
      List<String> res = mResult.getCommonPrefixes();
      return res.toArray(new String[res.size()]);
    }

    @Override
    public ObjectListingChunk getNextChunk() throws IOException {
      if (mResult.isTruncated()) {
        mRequest.setMarker(mResult.getNextMarker());
        ObjectListing nextResult = mClient.listObjects(mRequest);
        if (nextResult != null) {
          return new OBSObjectListingChunk(mRequest, nextResult);
        }
      }
      return null;
    }
  }

  @Override
  protected ObjectStatus getObjectStatus(String key) {
    try {
      ObjectMetadata meta = mClient.getObjectMetadata(mBucketName, key);
      if (meta == null) {
        return null;
      }
      return new ObjectStatus(key, meta.getEtag(), meta.getContentLength(),
          meta.getLastModified().getTime());
    } catch (ObsException e) {
      LOG.warn("Failed to get Object {}, return null", key, e);
      return null;
    }
  }

  // No ACL integration currently, returns default empty value
  @Override
  protected ObjectPermissions getPermissions() {
    return new ObjectPermissions("", "", Constants.DEFAULT_FILE_SYSTEM_MODE);
  }

  @Override
  protected String getRootKey() {
    return OBSConstants.HEADER_OBS + mBucketName;
  }

  @Override
  protected InputStream openObject(String key, OpenOptions options, RetryPolicy retryPolicy) throws IOException {
    try {
      return new OBSInputStream(mBucketName, key, mClient, options.getOffset(), retryPolicy,
          mUfsConf.getBytes(PropertyKey.UNDERFS_OBJECT_STORE_MULTI_RANGE_CHUNK_SIZE));
    } catch (ObsException e) {
      throw new IOException(e.getMessage());
    }
  }
}
