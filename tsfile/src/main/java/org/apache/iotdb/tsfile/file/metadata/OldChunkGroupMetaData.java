/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.tsfile.file.metadata;

import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata of ChunkGroup.
 */
public class OldChunkGroupMetaData {

  /**
   * Name of device, this field is not serialized.
   */
  private String deviceID;

  /**
   * Byte offset of the corresponding data in the file Notice: include the chunk group marker.
   * For Hadoop and Spark.
   */
  private long startOffsetOfChunkGroup;

  /**
   * End Byte position of the whole chunk group in the file Notice: position after the chunk group footer.
   * For Hadoop and Spark.
   */
  private long endOffsetOfChunkGroup;

  /**
   * All time series chunks in this chunk group.
   */
  private List<OldChunkMetadata> chunkMetaDataList;

  private long version;

  private OldChunkGroupMetaData() {
    chunkMetaDataList = new ArrayList<>();
  }

  /**
   * deserialize from ByteBuffer.
   *
   * @param buffer ByteBuffer
   * @return ChunkGroupMetaData object
   */
  public static OldChunkGroupMetaData deserializeFrom(ByteBuffer buffer) {
    OldChunkGroupMetaData chunkGroupMetaData = new OldChunkGroupMetaData();

    chunkGroupMetaData.deviceID = ReadWriteIOUtils.readString(buffer);
    chunkGroupMetaData.startOffsetOfChunkGroup = ReadWriteIOUtils.readLong(buffer);
    chunkGroupMetaData.endOffsetOfChunkGroup = ReadWriteIOUtils.readLong(buffer);
    chunkGroupMetaData.version = ReadWriteIOUtils.readLong(buffer);

    int size = ReadWriteIOUtils.readInt(buffer);

    List<OldChunkMetadata> chunkMetaDataList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      OldChunkMetadata metaData = OldChunkMetadata.deserializeFrom(buffer);
      chunkMetaDataList.add(metaData);
    }
    chunkGroupMetaData.chunkMetaDataList = chunkMetaDataList;

    return chunkGroupMetaData;
  }

  /**
   * add time series chunk metadata to list. THREAD NOT SAFE
   *
   * @param metadata time series metadata to add
   */
  public void addTimeSeriesChunkMetaData(OldChunkMetadata metadata) {
    if (chunkMetaDataList == null) {
      chunkMetaDataList = new ArrayList<>();
    }
    chunkMetaDataList.add(metadata);
  }

  public List<OldChunkMetadata> getChunkMetaDataList() {
    return chunkMetaDataList;
  }

  public String getDeviceID() {
    return deviceID;
  }

  public long getStartOffsetOfChunkGroup() {
    return startOffsetOfChunkGroup;
  }

  public long getEndOffsetOfChunkGroup() {
    return endOffsetOfChunkGroup;
  }

  public long getVersion() {
    return version;
  }

}