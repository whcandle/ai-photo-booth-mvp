package com.mg.booth.domain;

/**
 * V2 template reference DTO.
 * Represents template metadata resolved from local index.json for AI v2 processing.
 */
public class V2TemplateRef {

  private String templateCode;
  private String versionSemver;
  private String downloadUrl;
  private String checksumSha256;

  public V2TemplateRef() {
  }

  public V2TemplateRef(String templateCode, String versionSemver, String downloadUrl, String checksumSha256) {
    this.templateCode = templateCode;
    this.versionSemver = versionSemver;
    this.downloadUrl = downloadUrl;
    this.checksumSha256 = checksumSha256;
  }

  public String getTemplateCode() {
    return templateCode;
  }

  public void setTemplateCode(String templateCode) {
    this.templateCode = templateCode;
  }

  public String getVersionSemver() {
    return versionSemver;
  }

  public void setVersionSemver(String versionSemver) {
    this.versionSemver = versionSemver;
  }

  public String getDownloadUrl() {
    return downloadUrl;
  }

  public void setDownloadUrl(String downloadUrl) {
    this.downloadUrl = downloadUrl;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public void setChecksumSha256(String checksumSha256) {
    this.checksumSha256 = checksumSha256;
  }
}

