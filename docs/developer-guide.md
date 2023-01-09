# Subspace Infinity - Developer Guide

The modular design that Subspace Infinity uses mirrors that of Subspace Server .NET & ASSS and as such is meant to be completely customizable and extendable. Yes, you can always modify the core server itself by cloning the repository and making your own changes. However, more likely you will want to add your own custom functionality by writing modules that plug-in to the server. This document is to provide some guidance on how to do just that.

> For those familiar with ASSS, writing a module for Subspace Infinity should be a walk in the park. All of the same concepts apply, but there are some differences to be conscious of:
> - Data oriented instead of object oriented
> - Networking and packets is abstracted away by the SimEthereal library

# Modules

The server itself has a core set of systems and services running. The basic extension building block of the server is a module. A module is simply just a class that the server creates an instance of and calls methods on
