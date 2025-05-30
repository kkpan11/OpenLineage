/*
/* Copyright 2018-2025 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.client.utils;

import java.util.LinkedList;
import java.util.List;
import lombok.Value;

@Value
public class DatasetIdentifier {
  String name;
  String namespace;
  List<Symlink> symlinks;

  public enum SymlinkType {
    TABLE,
    LOCATION
  };

  public DatasetIdentifier(String name, String namespace) {
    this.name = name;
    this.namespace = namespace;
    this.symlinks = new LinkedList<>();
  }

  public DatasetIdentifier(String name, String namespace, List<Symlink> symlinks) {
    this.name = name;
    this.namespace = namespace;
    this.symlinks = symlinks;
  }

  public DatasetIdentifier withSymlink(String name, String namespace, SymlinkType type) {
    symlinks.add(new Symlink(name, namespace, type));
    return this;
  }

  public DatasetIdentifier withSymlink(Symlink symlink) {
    symlinks.add(symlink);
    return this;
  }

  @Value
  public static class Symlink {
    String name;
    String namespace;
    SymlinkType type;
  }
}
