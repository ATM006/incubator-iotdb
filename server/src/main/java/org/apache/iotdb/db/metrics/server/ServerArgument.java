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
package org.apache.iotdb.db.metrics.server;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerArgument {

  private static final Logger logger = LoggerFactory.getLogger(ServerArgument.class);
  private static final int CPU_TIME = 1000;
  private static final int CPU_ABNORMAL_VALUE = -1;

  private String host;
  private int port;
  private int cores;
  private long totalMemory;
  private long freeMemory;
  private long maxMemory;
  private String osName;
  private long totalPhysicalMemory;
  private long freePhysicalMemory;
  private long usedPhysicalMemory;
  private int cpuRatio;

  public ServerArgument(int port) {
    this.port = port;
    this.host = inferHostname();
    this.cores = totalCores();
    this.osName = osName();
    this.totalPhysicalMemory = totalPhysicalMemory();
    this.usedPhysicalMemory = usedPhysicalMemory();
    this.freePhysicalMemory = freePhysicalMemory();
    this.totalMemory = totalMemory();
    this.freeMemory = freeMemory();
    this.maxMemory = maxMemory();
    this.cpuRatio = getCpuRatio();
    if (!osName.toLowerCase().contains("windows") && !osName.contains("linux")) {
      logger.warn("Can't get the cpu ratio,because this OS:{} is not support", osName);
    }
  }

  private String inferHostname() {
    InetAddress ia = null;
    try {
      ia = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      logger.error("The host is unknow", e);
    }
    return ia != null ? ia.getHostName() : null;
  }

  private String osName() {
    return System.getProperty("os.name");
  }

  private int totalCores() {
    OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    return osmxb.getAvailableProcessors();
  }

  long totalMemory() {
    return Runtime.getRuntime().totalMemory() / 1024 / 1024;
  }

  long freeMemory() {
    return Runtime.getRuntime().freeMemory() / 1024 / 1024;
  }

  long maxMemory() {
    return Runtime.getRuntime().maxMemory() / 1024 / 1024;
  }

  long totalPhysicalMemory() {
    OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    return osmxb.getTotalPhysicalMemorySize() / 1024 / 1024;
  }

  long usedPhysicalMemory() {
    OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    return (osmxb.getTotalPhysicalMemorySize() - osmxb.getFreePhysicalMemorySize()) / 1024 / 1024;
  }

  long freePhysicalMemory() {
    OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    return osmxb.getFreePhysicalMemorySize() / 1024 / 1024;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public int getCores() {
    return cores;
  }

  public long getTotalMemory() {
    return totalMemory;
  }

  public long getFreeMemory() {
    return freeMemory;
  }

  public long getMaxMemory() {
    return maxMemory;
  }

  public String getOsName() {
    return osName;
  }

  public long getTotalPhysicalMemory() {
    return totalPhysicalMemory;
  }

  public long getFreePhysicalMemory() {
    return freePhysicalMemory;
  }

  public long getUsedPhysicalMemory() {
    return usedPhysicalMemory;
  }

  public int getCpuRatio() {
    String osNameStr = System.getProperty("os.name").toLowerCase();
    cpuRatio = 0;
    if (osNameStr.contains("windows")) {
      cpuRatio = getCpuRatioForWindows();
    } else if (osNameStr.contains("linux")) {
      cpuRatio = getCpuRateForLinux();
    } else {
      cpuRatio = CPU_ABNORMAL_VALUE;
    }
    return cpuRatio;
  }

  /**
   * cpu ratio for linux
   */
  private int getCpuRateForLinux() {
    try {
      long[] c0 = readLinuxCpu();
      Thread.sleep(CPU_TIME);
      long[] c1 = readLinuxCpu();
      if (c0 != null && c1 != null) {
        long idleCpuTime = c1[0] - c0[0];
        long totalCpuTime = c1[1] - c0[1];
        if (totalCpuTime == 0) {
          return 100;
        }
        return (int)(100 * (1 - (double)idleCpuTime / totalCpuTime));
      } else {
        return 0;
      }
    } catch (Exception e) {
      logger.error("Get CPU Ratio failed", e);
      return 0;
    }
  }

  /**
   * cpu ratio for windows
   */
  private int getCpuRatioForWindows() {
    try {
      String procCmd = System.getenv("windir") + "\\system32\\wbem\\wmic.exe process get Caption,CommandLine,"
          + "KernelModeTime,ReadOperationCount,ThreadCount,UserModeTime,WriteOperationCount";
      long[] c0 = readWinCpu(Runtime.getRuntime().exec(procCmd));
      Thread.sleep(CPU_TIME);
      long[] c1 = readWinCpu(Runtime.getRuntime().exec(procCmd));
      if (c0 != null && c1 != null) {
        long idletime = c1[0] - c0[0];
        long busytime = c1[1] - c0[1];
        if ((busytime + idletime) == 0) {
          return 100;
        }
        return (int)(100 * ((double)busytime / (busytime + idletime)));
      } else {
        return 0;
      }
    } catch (Exception e) {
      logger.error("Get CPU Ratio failed", e);
      return 0;
    }
  }

  /**
   * read cpu info(windows)
   */
  private long[] readWinCpu(final Process proc) throws Exception {
    long[] retn = new long[2];
    proc.getOutputStream().close();
    InputStreamReader ir = new InputStreamReader(proc.getInputStream());
    LineNumberReader input = new LineNumberReader(ir);
    String line = input.readLine();
    if (line == null || line.length() < 10) {
      return null;
    }
    int capidx = line.indexOf("Caption");
    int cmdidx = line.indexOf("CommandLine");
    int rocidx = line.indexOf("ReadOperationCount");
    int umtidx = line.indexOf("UserModeTime");
    int kmtidx = line.indexOf("KernelModeTime");
    int wocidx = line.indexOf("WriteOperationCount");
    long idletime = 0;
    long kneltime = 0;
    long usertime = 0;
    while ((line = input.readLine()) != null) {
      if (line.length() < wocidx) {
        continue;
      }
      String cmd = line.substring(cmdidx, kmtidx).trim();
      if (cmd.contains("wmic.exe")) {
        continue;
      }
      String caption = line.substring(capidx, cmdidx).trim();
      String s1 = line.substring(kmtidx, rocidx).trim();
      String s2 = line.substring(umtidx, wocidx).trim();
      List<String> digitS1 = new ArrayList<>();
      List<String> digitS2 = new ArrayList<>();
      digitS1.add(s1.replaceAll("\\D", ""));
      digitS2.add(s2.replaceAll("\\D", ""));
      if (caption.equals("System Idle Process") || caption.equals("System")) {
        if (s1.length() > 0) {
          if (!digitS1.get(0).equals("") && digitS1.get(0) != null) {
            idletime += Long.parseLong(digitS1.get(0));
          }
        }
        if (s2.length() > 0) {
          if (!digitS2.get(0).equals("") && digitS2.get(0) != null) {
            idletime += Long.parseLong(digitS2.get(0));
          }
        }
        continue;
      }
      if (s1.length() > 0) {
        if (!digitS1.get(0).equals("") && digitS1.get(0) != null) {
          kneltime += Long.parseLong(digitS1.get(0));
        }
      }
      if (s2.length() > 0) {
        if (!digitS2.get(0).equals("") && digitS2.get(0) != null) {
          kneltime += Long.parseLong(digitS2.get(0));
        }
      }
    }
    retn[0] = idletime;
    retn[1] = kneltime + usertime;
    proc.getInputStream().close();
    return retn;
  }

  /**
   * read cpu info(linux)
   */
  private long[] readLinuxCpu() throws Exception {
    long[] retn = new long[2];
    BufferedReader buffer = null;
    long idleCpuTime = 0;
    long totalCpuTime = 0;
    buffer = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")));
    String line = null;
    while ((line = buffer.readLine()) != null) {
      if (line.startsWith("cpu")) {
        StringTokenizer tokenizer = new StringTokenizer(line);
        List<String> temp = new ArrayList<String>();
        while (tokenizer.hasMoreElements()) {
          temp.add(tokenizer.nextToken());
        }
        idleCpuTime = Long.parseLong(temp.get(4));
        totalCpuTime = Long.parseLong(temp.get(1)) + Long.parseLong(temp.get(2))
            + Long.parseLong(temp.get(3)) + Long.parseLong(temp.get(4));
        break;
      }
    }
    retn[0] = idleCpuTime;
    retn[1] = totalCpuTime;
    buffer.close();
    return retn;
  }

}
