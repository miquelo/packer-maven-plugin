#!/bin/sh

echo "$$"
packer -machine-readable $@
