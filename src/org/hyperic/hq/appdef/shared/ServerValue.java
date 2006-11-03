/*
 * xdoclet generated code.
 * legacy DTO pattern (targeted to be replaced with hibernate pojo).
 */
package org.hyperic.hq.appdef.shared;

import java.util.Collection;

/**
 * Value object for Server.
 *
 */
public class ServerValue
   extends org.hyperic.hq.appdef.shared.AppdefResourceValue
   implements java.io.Serializable
{
   private java.lang.String sortName;
   private boolean sortNameHasBeenSet = false;
   private boolean runtimeAutodiscovery;
   private boolean runtimeAutodiscoveryHasBeenSet = false;
   private boolean wasAutodiscovered;
   private boolean wasAutodiscoveredHasBeenSet = false;
   private boolean autodiscoveryZombie;
   private boolean autodiscoveryZombieHasBeenSet = false;
   private java.lang.Integer configResponseId;
   private boolean configResponseIdHasBeenSet = false;
   private java.lang.String modifiedBy;
   private boolean modifiedByHasBeenSet = false;
   private java.lang.String owner;
   private boolean ownerHasBeenSet = false;
   private java.lang.String location;
   private boolean locationHasBeenSet = false;
   private java.lang.String name;
   private boolean nameHasBeenSet = false;
   private java.lang.String autoinventoryIdentifier;
   private boolean autoinventoryIdentifierHasBeenSet = false;
   private java.lang.String installPath;
   private boolean installPathHasBeenSet = false;
   private java.lang.String description;
   private boolean descriptionHasBeenSet = false;
   private boolean servicesAutomanaged;
   private boolean servicesAutomanagedHasBeenSet = false;
   private java.lang.Integer id;
   private boolean idHasBeenSet = false;
   private java.lang.Long mTime;
   private boolean mTimeHasBeenSet = false;
   private java.lang.Long cTime;
   private boolean cTimeHasBeenSet = false;
   private Collection ServiceValues = new java.util.HashSet();
   private org.hyperic.hq.appdef.shared.ServerTypeValue ServerType;
   private boolean ServerTypeHasBeenSet = false;
   private org.hyperic.hq.appdef.shared.PlatformLightValue Platform;
   private boolean PlatformHasBeenSet = false;

   public ServerValue()
   {
   }

   public ServerValue( java.lang.String sortName,boolean runtimeAutodiscovery,boolean wasAutodiscovered,boolean autodiscoveryZombie,java.lang.Integer configResponseId,java.lang.String modifiedBy,java.lang.String owner,java.lang.String location,java.lang.String name,java.lang.String autoinventoryIdentifier,java.lang.String installPath,java.lang.String description,boolean servicesAutomanaged,java.lang.Integer id,java.lang.Long mTime,java.lang.Long cTime )
   {
	  this.sortName = sortName;
	  sortNameHasBeenSet = true;
	  this.runtimeAutodiscovery = runtimeAutodiscovery;
	  runtimeAutodiscoveryHasBeenSet = true;
	  this.wasAutodiscovered = wasAutodiscovered;
	  wasAutodiscoveredHasBeenSet = true;
	  this.autodiscoveryZombie = autodiscoveryZombie;
	  autodiscoveryZombieHasBeenSet = true;
	  this.configResponseId = configResponseId;
	  configResponseIdHasBeenSet = true;
	  this.modifiedBy = modifiedBy;
	  modifiedByHasBeenSet = true;
	  this.owner = owner;
	  ownerHasBeenSet = true;
	  this.location = location;
	  locationHasBeenSet = true;
	  this.name = name;
	  nameHasBeenSet = true;
	  this.autoinventoryIdentifier = autoinventoryIdentifier;
	  autoinventoryIdentifierHasBeenSet = true;
	  this.installPath = installPath;
	  installPathHasBeenSet = true;
	  this.description = description;
	  descriptionHasBeenSet = true;
	  this.servicesAutomanaged = servicesAutomanaged;
	  servicesAutomanagedHasBeenSet = true;
	  this.id = id;
	  idHasBeenSet = true;
	  this.mTime = mTime;
	  mTimeHasBeenSet = true;
	  this.cTime = cTime;
	  cTimeHasBeenSet = true;
   }

   //TODO Cloneable is better than this !
   public ServerValue( ServerValue otherValue )
   {
	  this.sortName = otherValue.sortName;
	  sortNameHasBeenSet = true;
	  this.runtimeAutodiscovery = otherValue.runtimeAutodiscovery;
	  runtimeAutodiscoveryHasBeenSet = true;
	  this.wasAutodiscovered = otherValue.wasAutodiscovered;
	  wasAutodiscoveredHasBeenSet = true;
	  this.autodiscoveryZombie = otherValue.autodiscoveryZombie;
	  autodiscoveryZombieHasBeenSet = true;
	  this.configResponseId = otherValue.configResponseId;
	  configResponseIdHasBeenSet = true;
	  this.modifiedBy = otherValue.modifiedBy;
	  modifiedByHasBeenSet = true;
	  this.owner = otherValue.owner;
	  ownerHasBeenSet = true;
	  this.location = otherValue.location;
	  locationHasBeenSet = true;
	  this.name = otherValue.name;
	  nameHasBeenSet = true;
	  this.autoinventoryIdentifier = otherValue.autoinventoryIdentifier;
	  autoinventoryIdentifierHasBeenSet = true;
	  this.installPath = otherValue.installPath;
	  installPathHasBeenSet = true;
	  this.description = otherValue.description;
	  descriptionHasBeenSet = true;
	  this.servicesAutomanaged = otherValue.servicesAutomanaged;
	  servicesAutomanagedHasBeenSet = true;
	  this.id = otherValue.id;
	  idHasBeenSet = true;
	  this.mTime = otherValue.mTime;
	  mTimeHasBeenSet = true;
	  this.cTime = otherValue.cTime;
	  cTimeHasBeenSet = true;
	// TODO Clone is better no ?
	  this.ServiceValues = otherValue.ServiceValues;
	// TODO Clone is better no ?
	  this.ServerType = otherValue.ServerType;
	  ServerTypeHasBeenSet = true;
	// TODO Clone is better no ?
	  this.Platform = otherValue.Platform;
	  PlatformHasBeenSet = true;

   }

   public java.lang.String getSortName()
   {
	  return this.sortName;
   }

   public void setSortName( java.lang.String sortName )
   {
	  this.sortName = sortName;
	  sortNameHasBeenSet = true;

   }

   public boolean sortNameHasBeenSet(){
	  return sortNameHasBeenSet;
   }
   public boolean getRuntimeAutodiscovery()
   {
	  return this.runtimeAutodiscovery;
   }

   public void setRuntimeAutodiscovery( boolean runtimeAutodiscovery )
   {
	  this.runtimeAutodiscovery = runtimeAutodiscovery;
	  runtimeAutodiscoveryHasBeenSet = true;

   }

   public boolean runtimeAutodiscoveryHasBeenSet(){
	  return runtimeAutodiscoveryHasBeenSet;
   }
   public boolean getWasAutodiscovered()
   {
	  return this.wasAutodiscovered;
   }

   public void setWasAutodiscovered( boolean wasAutodiscovered )
   {
	  this.wasAutodiscovered = wasAutodiscovered;
	  wasAutodiscoveredHasBeenSet = true;

   }

   public boolean wasAutodiscoveredHasBeenSet(){
	  return wasAutodiscoveredHasBeenSet;
   }
   public boolean getAutodiscoveryZombie()
   {
	  return this.autodiscoveryZombie;
   }

   public void setAutodiscoveryZombie( boolean autodiscoveryZombie )
   {
	  this.autodiscoveryZombie = autodiscoveryZombie;
	  autodiscoveryZombieHasBeenSet = true;

   }

   public boolean autodiscoveryZombieHasBeenSet(){
	  return autodiscoveryZombieHasBeenSet;
   }
   public java.lang.Integer getConfigResponseId()
   {
	  return this.configResponseId;
   }

   public void setConfigResponseId( java.lang.Integer configResponseId )
   {
	  this.configResponseId = configResponseId;
	  configResponseIdHasBeenSet = true;

   }

   public boolean configResponseIdHasBeenSet(){
	  return configResponseIdHasBeenSet;
   }
   public java.lang.String getModifiedBy()
   {
	  return this.modifiedBy;
   }

   public void setModifiedBy( java.lang.String modifiedBy )
   {
	  this.modifiedBy = modifiedBy;
	  modifiedByHasBeenSet = true;

   }

   public boolean modifiedByHasBeenSet(){
	  return modifiedByHasBeenSet;
   }
   public java.lang.String getOwner()
   {
	  return this.owner;
   }

   public void setOwner( java.lang.String owner )
   {
	  this.owner = owner;
	  ownerHasBeenSet = true;

   }

   public boolean ownerHasBeenSet(){
	  return ownerHasBeenSet;
   }
   public java.lang.String getLocation()
   {
	  return this.location;
   }

   public void setLocation( java.lang.String location )
   {
	  this.location = location;
	  locationHasBeenSet = true;

   }

   public boolean locationHasBeenSet(){
	  return locationHasBeenSet;
   }
   public java.lang.String getName()
   {
	  return this.name;
   }

   public void setName( java.lang.String name )
   {
	  this.name = name;
	  nameHasBeenSet = true;

   }

   public boolean nameHasBeenSet(){
	  return nameHasBeenSet;
   }
   public java.lang.String getAutoinventoryIdentifier()
   {
	  return this.autoinventoryIdentifier;
   }

   public void setAutoinventoryIdentifier( java.lang.String autoinventoryIdentifier )
   {
	  this.autoinventoryIdentifier = autoinventoryIdentifier;
	  autoinventoryIdentifierHasBeenSet = true;

   }

   public boolean autoinventoryIdentifierHasBeenSet(){
	  return autoinventoryIdentifierHasBeenSet;
   }
   public java.lang.String getInstallPath()
   {
	  return this.installPath;
   }

   public void setInstallPath( java.lang.String installPath )
   {
	  this.installPath = installPath;
	  installPathHasBeenSet = true;

   }

   public boolean installPathHasBeenSet(){
	  return installPathHasBeenSet;
   }
   public java.lang.String getDescription()
   {
	  return this.description;
   }

   public void setDescription( java.lang.String description )
   {
	  this.description = description;
	  descriptionHasBeenSet = true;

   }

   public boolean descriptionHasBeenSet(){
	  return descriptionHasBeenSet;
   }
   public boolean getServicesAutomanaged()
   {
	  return this.servicesAutomanaged;
   }

   public void setServicesAutomanaged( boolean servicesAutomanaged )
   {
	  this.servicesAutomanaged = servicesAutomanaged;
	  servicesAutomanagedHasBeenSet = true;

   }

   public boolean servicesAutomanagedHasBeenSet(){
	  return servicesAutomanagedHasBeenSet;
   }
   public java.lang.Integer getId()
   {
	  return this.id;
   }

   public void setId( java.lang.Integer id )
   {
	  this.id = id;
	  idHasBeenSet = true;
   }

   public boolean idHasBeenSet(){
	  return idHasBeenSet;
   }
   public java.lang.Long getMTime()
   {
	  return this.mTime;
   }

   public void setMTime( java.lang.Long mTime )
   {
	  this.mTime = mTime;
	  mTimeHasBeenSet = true;

   }

   public boolean mTimeHasBeenSet(){
	  return mTimeHasBeenSet;
   }
   public java.lang.Long getCTime()
   {
	  return this.cTime;
   }

   public void setCTime( java.lang.Long cTime )
   {
	  this.cTime = cTime;
	  cTimeHasBeenSet = true;

   }

   public boolean cTimeHasBeenSet(){
	  return cTimeHasBeenSet;
   }

   protected Collection addedServiceValues = new java.util.HashSet();
   protected Collection removedServiceValues = new java.util.HashSet();
   protected Collection updatedServiceValues = new java.util.HashSet();

   public Collection getAddedServiceValues() { return addedServiceValues; }
   public Collection getRemovedServiceValues() { return removedServiceValues; }
   public Collection getUpdatedServiceValues() { return updatedServiceValues; }

   public org.hyperic.hq.appdef.shared.ServiceLightValue[] getServiceValues()
   {
	  return (org.hyperic.hq.appdef.shared.ServiceLightValue[])this.ServiceValues.toArray(new org.hyperic.hq.appdef.shared.ServiceLightValue[ServiceValues.size()]);
   }

   public void addServiceValue(org.hyperic.hq.appdef.shared.ServiceLightValue added)
   {
	  this.ServiceValues.add(added);
	  if ( ! this.addedServiceValues.contains(added))
		 this.addedServiceValues.add(added);
   }

   public void removeServiceValue(org.hyperic.hq.appdef.shared.ServiceLightValue removed)
   {
	  this.ServiceValues.remove(removed);
	  this.removedServiceValues.add(removed);
	  if (this.addedServiceValues.contains(removed))
		 this.addedServiceValues.remove(removed);
	  if (this.updatedServiceValues.contains(removed))
		 this.updatedServiceValues.remove(removed);
   }

   public void removeAllServiceValues()
   {
        // DOH. Clear the collection - javier 2/24/03
        this.ServiceValues.clear();
   }

   public void updateServiceValue(org.hyperic.hq.appdef.shared.ServiceLightValue updated)
   {
	  if ( ! this.updatedServiceValues.contains(updated))
		 this.updatedServiceValues.add(updated);
   }

   public void cleanServiceValue(){
	  this.addedServiceValues = new java.util.HashSet();
	  this.removedServiceValues = new java.util.HashSet();
	  this.updatedServiceValues = new java.util.HashSet();
   }

   public void copyServiceValuesFrom(org.hyperic.hq.appdef.shared.ServerValue from)
   {
	  // TODO Clone the List ????
	  this.ServiceValues = from.ServiceValues;
   }
   public org.hyperic.hq.appdef.shared.ServerTypeValue getServerType()
   {
	  return this.ServerType;
   }
   public void setServerType( org.hyperic.hq.appdef.shared.ServerTypeValue ServerType )
   {
	  this.ServerType = ServerType;
	  ServerTypeHasBeenSet = true;
   }
   public org.hyperic.hq.appdef.shared.PlatformLightValue getPlatform()
   {
	  return this.Platform;
   }
   public void setPlatform( org.hyperic.hq.appdef.shared.PlatformLightValue Platform )
   {
	  this.Platform = Platform;
	  PlatformHasBeenSet = true;
   }

   public String toString()
   {
	  StringBuffer str = new StringBuffer("{");

	  str.append("sortName=" + getSortName() + " " + "runtimeAutodiscovery=" + getRuntimeAutodiscovery() + " " + "wasAutodiscovered=" + getWasAutodiscovered() + " " + "autodiscoveryZombie=" + getAutodiscoveryZombie() + " " + "configResponseId=" + getConfigResponseId() + " " + "modifiedBy=" + getModifiedBy() + " " + "owner=" + getOwner() + " " + "location=" + getLocation() + " " + "name=" + getName() + " " + "autoinventoryIdentifier=" + getAutoinventoryIdentifier() + " " + "installPath=" + getInstallPath() + " " + "description=" + getDescription() + " " + "servicesAutomanaged=" + getServicesAutomanaged() + " " + "id=" + getId() + " " + "mTime=" + getMTime() + " " + "cTime=" + getCTime());
	  str.append('}');

	  return(str.toString());
   }

   /**
	* A Value object have an identity if its attributes making its Primary Key
	* has all been set.  One object without identity is never equal to any other
	* object.
	*
	* @return true if this instance have an identity.
	*/
   protected boolean hasIdentity()
   {
	  boolean ret = true;
	  ret = ret && idHasBeenSet;
	  return ret;
   }

   public boolean equals(Object other)
   {
	  if ( ! hasIdentity() ) return false;
	  if (other instanceof ServerValue)
	  {
		 ServerValue that = (ServerValue) other;
		 if ( ! that.hasIdentity() ) return false;
		 boolean lEquals = true;
		 if( this.id == null )
		 {
			lEquals = lEquals && ( that.id == null );
		 }
		 else
		 {
			lEquals = lEquals && this.id.equals( that.id );
		 }

		 lEquals = lEquals && isIdentical(that);

		 return lEquals;
	  }
	  else
	  {
		 return false;
	  }
   }

   public boolean isIdentical(Object other)
   {
	  if (other instanceof ServerValue)
	  {
		 ServerValue that = (ServerValue) other;
		 boolean lEquals = true;
		 if( this.sortName == null )
		 {
			lEquals = lEquals && ( that.sortName == null );
		 }
		 else
		 {
			lEquals = lEquals && this.sortName.equals( that.sortName );
		 }
		 lEquals = lEquals && this.runtimeAutodiscovery == that.runtimeAutodiscovery;
		 lEquals = lEquals && this.wasAutodiscovered == that.wasAutodiscovered;
		 lEquals = lEquals && this.autodiscoveryZombie == that.autodiscoveryZombie;
		 if( this.configResponseId == null )
		 {
			lEquals = lEquals && ( that.configResponseId == null );
		 }
		 else
		 {
			lEquals = lEquals && this.configResponseId.equals( that.configResponseId );
		 }
		 if( this.modifiedBy == null )
		 {
			lEquals = lEquals && ( that.modifiedBy == null );
		 }
		 else
		 {
			lEquals = lEquals && this.modifiedBy.equals( that.modifiedBy );
		 }
		 if( this.owner == null )
		 {
			lEquals = lEquals && ( that.owner == null );
		 }
		 else
		 {
			lEquals = lEquals && this.owner.equals( that.owner );
		 }
		 if( this.location == null )
		 {
			lEquals = lEquals && ( that.location == null );
		 }
		 else
		 {
			lEquals = lEquals && this.location.equals( that.location );
		 }
		 if( this.name == null )
		 {
			lEquals = lEquals && ( that.name == null );
		 }
		 else
		 {
			lEquals = lEquals && this.name.equals( that.name );
		 }
		 if( this.autoinventoryIdentifier == null )
		 {
			lEquals = lEquals && ( that.autoinventoryIdentifier == null );
		 }
		 else
		 {
			lEquals = lEquals && this.autoinventoryIdentifier.equals( that.autoinventoryIdentifier );
		 }
		 if( this.installPath == null )
		 {
			lEquals = lEquals && ( that.installPath == null );
		 }
		 else
		 {
			lEquals = lEquals && this.installPath.equals( that.installPath );
		 }
		 if( this.description == null )
		 {
			lEquals = lEquals && ( that.description == null );
		 }
		 else
		 {
			lEquals = lEquals && this.description.equals( that.description );
		 }
		 lEquals = lEquals && this.servicesAutomanaged == that.servicesAutomanaged;
		 if( this.mTime == null )
		 {
			lEquals = lEquals && ( that.mTime == null );
		 }
		 else
		 {
			lEquals = lEquals && this.mTime.equals( that.mTime );
		 }
		 if( this.cTime == null )
		 {
			lEquals = lEquals && ( that.cTime == null );
		 }
		 else
		 {
			lEquals = lEquals && this.cTime.equals( that.cTime );
		 }
		 if( this.getServiceValues() == null )
		 {
			lEquals = lEquals && ( that.getServiceValues() == null );
		 }
		 else
		 {
            // XXX Covalent Custom - dont compare the arrays, as order is not significant. ever.    
            // - javier 7/16/03
            java.util.Collection cmr1 = java.util.Arrays.asList(this.getServiceValues());
            java.util.Collection cmr2 = java.util.Arrays.asList(that.getServiceValues());
			// lEquals = lEquals && java.util.Arrays.equals(this.getServiceValues() , that.getServiceValues()) ;
            lEquals = lEquals && cmr1.containsAll(cmr2);
		 }
		 if( this.ServerType == null )
		 {
			lEquals = lEquals && ( that.ServerType == null );
		 }
		 else
		 {
			lEquals = lEquals && this.ServerType.equals( that.ServerType );
		 }
		 if( this.Platform == null )
		 {
			lEquals = lEquals && ( that.Platform == null );
		 }
		 else
		 {
			lEquals = lEquals && this.Platform.equals( that.Platform );
		 }

		 return lEquals;
	  }
	  else
	  {
		 return false;
	  }
   }

   public int hashCode(){
	  int result = 17;
      result = 37*result + ((this.sortName != null) ? this.sortName.hashCode() : 0);

      result = 37*result + (runtimeAutodiscovery ? 0 : 1);

      result = 37*result + (wasAutodiscovered ? 0 : 1);

      result = 37*result + (autodiscoveryZombie ? 0 : 1);

      result = 37*result + ((this.configResponseId != null) ? this.configResponseId.hashCode() : 0);

      result = 37*result + ((this.modifiedBy != null) ? this.modifiedBy.hashCode() : 0);

      result = 37*result + ((this.owner != null) ? this.owner.hashCode() : 0);

      result = 37*result + ((this.location != null) ? this.location.hashCode() : 0);

      result = 37*result + ((this.name != null) ? this.name.hashCode() : 0);

      result = 37*result + ((this.autoinventoryIdentifier != null) ? this.autoinventoryIdentifier.hashCode() : 0);

      result = 37*result + ((this.installPath != null) ? this.installPath.hashCode() : 0);

      result = 37*result + ((this.description != null) ? this.description.hashCode() : 0);

      result = 37*result + (servicesAutomanaged ? 0 : 1);

      result = 37*result + ((this.id != null) ? this.id.hashCode() : 0);

      result = 37*result + ((this.mTime != null) ? this.mTime.hashCode() : 0);

      result = 37*result + ((this.cTime != null) ? this.cTime.hashCode() : 0);

	  result = 37*result + ((this.getServiceValues() != null) ? this.getServiceValues().hashCode() : 0);
	  result = 37*result + ((this.ServerType != null) ? this.ServerType.hashCode() : 0);
	  result = 37*result + ((this.Platform != null) ? this.Platform.hashCode() : 0);
	  return result;
   }

}
