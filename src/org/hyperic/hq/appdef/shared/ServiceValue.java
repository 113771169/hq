/*
 * xdoclet generated code.
 * legacy DTO pattern (targeted to be replaced with hibernate pojo).
 */
package org.hyperic.hq.appdef.shared;

/**
 * Value object for Service.
 *
 */
public class ServiceValue
   extends org.hyperic.hq.appdef.shared.AppdefResourceValue
   implements java.io.Serializable
{
   private java.lang.String sortName;
   private boolean sortNameHasBeenSet = false;
   private boolean autodiscoveryZombie;
   private boolean autodiscoveryZombieHasBeenSet = false;
   private boolean serviceRt;
   private boolean serviceRtHasBeenSet = false;
   private boolean endUserRt;
   private boolean endUserRtHasBeenSet = false;
   private java.lang.String modifiedBy;
   private boolean modifiedByHasBeenSet = false;
   private java.lang.String owner;
   private boolean ownerHasBeenSet = false;
   private java.lang.String location;
   private boolean locationHasBeenSet = false;
   private java.lang.Integer configResponseId;
   private boolean configResponseIdHasBeenSet = false;
   private Integer parentId;
   private boolean parentIdHasBeenSet = false;
   private java.lang.String name;
   private boolean nameHasBeenSet = false;
   private java.lang.String description;
   private boolean descriptionHasBeenSet = false;
   private java.lang.Integer id;
   private boolean idHasBeenSet = false;
   private java.lang.Long mTime;
   private boolean mTimeHasBeenSet = false;
   private java.lang.Long cTime;
   private boolean cTimeHasBeenSet = false;
   private org.hyperic.hq.appdef.shared.ServerLightValue Server;
   private boolean ServerHasBeenSet = false;
   private org.hyperic.hq.appdef.shared.ServiceClusterValue ServiceCluster;
   private boolean ServiceClusterHasBeenSet = false;
   private org.hyperic.hq.appdef.shared.ServiceTypeValue ServiceType;
   private boolean ServiceTypeHasBeenSet = false;

   public ServiceValue()
   {
   }

   public ServiceValue( java.lang.String sortName,boolean autodiscoveryZombie,boolean serviceRt,boolean endUserRt,java.lang.String modifiedBy,java.lang.String owner,java.lang.String location,java.lang.Integer configResponseId,Integer parentId,java.lang.String name,java.lang.String description,java.lang.Integer id,java.lang.Long mTime,java.lang.Long cTime )
   {
	  this.sortName = sortName;
	  sortNameHasBeenSet = true;
	  this.autodiscoveryZombie = autodiscoveryZombie;
	  autodiscoveryZombieHasBeenSet = true;
	  this.serviceRt = serviceRt;
	  serviceRtHasBeenSet = true;
	  this.endUserRt = endUserRt;
	  endUserRtHasBeenSet = true;
	  this.modifiedBy = modifiedBy;
	  modifiedByHasBeenSet = true;
	  this.owner = owner;
	  ownerHasBeenSet = true;
	  this.location = location;
	  locationHasBeenSet = true;
	  this.configResponseId = configResponseId;
	  configResponseIdHasBeenSet = true;
	  this.parentId = parentId;
	  parentIdHasBeenSet = true;
	  this.name = name;
	  nameHasBeenSet = true;
	  this.description = description;
	  descriptionHasBeenSet = true;
	  this.id = id;
	  idHasBeenSet = true;
	  this.mTime = mTime;
	  mTimeHasBeenSet = true;
	  this.cTime = cTime;
	  cTimeHasBeenSet = true;
   }

   //TODO Cloneable is better than this !
   public ServiceValue( ServiceValue otherValue )
   {
	  this.sortName = otherValue.sortName;
	  sortNameHasBeenSet = true;
	  this.autodiscoveryZombie = otherValue.autodiscoveryZombie;
	  autodiscoveryZombieHasBeenSet = true;
	  this.serviceRt = otherValue.serviceRt;
	  serviceRtHasBeenSet = true;
	  this.endUserRt = otherValue.endUserRt;
	  endUserRtHasBeenSet = true;
	  this.modifiedBy = otherValue.modifiedBy;
	  modifiedByHasBeenSet = true;
	  this.owner = otherValue.owner;
	  ownerHasBeenSet = true;
	  this.location = otherValue.location;
	  locationHasBeenSet = true;
	  this.configResponseId = otherValue.configResponseId;
	  configResponseIdHasBeenSet = true;
	  this.parentId = otherValue.parentId;
	  parentIdHasBeenSet = true;
	  this.name = otherValue.name;
	  nameHasBeenSet = true;
	  this.description = otherValue.description;
	  descriptionHasBeenSet = true;
	  this.id = otherValue.id;
	  idHasBeenSet = true;
	  this.mTime = otherValue.mTime;
	  mTimeHasBeenSet = true;
	  this.cTime = otherValue.cTime;
	  cTimeHasBeenSet = true;
	// TODO Clone is better no ?
	  this.Server = otherValue.Server;
	  ServerHasBeenSet = true;
	// TODO Clone is better no ?
	  this.ServiceCluster = otherValue.ServiceCluster;
	  ServiceClusterHasBeenSet = true;
	// TODO Clone is better no ?
	  this.ServiceType = otherValue.ServiceType;
	  ServiceTypeHasBeenSet = true;
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
   public boolean getServiceRt()
   {
	  return this.serviceRt;
   }

   public void setServiceRt( boolean serviceRt )
   {
	  this.serviceRt = serviceRt;
	  serviceRtHasBeenSet = true;

   }

   public boolean serviceRtHasBeenSet(){
	  return serviceRtHasBeenSet;
   }
   public boolean getEndUserRt()
   {
	  return this.endUserRt;
   }

   public void setEndUserRt( boolean endUserRt )
   {
	  this.endUserRt = endUserRt;
	  endUserRtHasBeenSet = true;

   }

   public boolean endUserRtHasBeenSet(){
	  return endUserRtHasBeenSet;
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
   public Integer getParentId()
   {
	  return this.parentId;
   }

   public void setParentId( Integer parentId )
   {
	  this.parentId = parentId;
	  parentIdHasBeenSet = true;

   }

   public boolean parentIdHasBeenSet(){
	  return parentIdHasBeenSet;
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

   public org.hyperic.hq.appdef.shared.ServerLightValue getServer()
   {
	  return this.Server;
   }
   public void setServer( org.hyperic.hq.appdef.shared.ServerLightValue Server )
   {
	  this.Server = Server;
	  ServerHasBeenSet = true;
   }
   public org.hyperic.hq.appdef.shared.ServiceClusterValue getServiceCluster()
   {
	  return this.ServiceCluster;
   }
   public void setServiceCluster( org.hyperic.hq.appdef.shared.ServiceClusterValue ServiceCluster )
   {
	  this.ServiceCluster = ServiceCluster;
	  ServiceClusterHasBeenSet = true;
   }
   public org.hyperic.hq.appdef.shared.ServiceTypeValue getServiceType()
   {
	  return this.ServiceType;
   }
   public void setServiceType( org.hyperic.hq.appdef.shared.ServiceTypeValue ServiceType )
   {
	  this.ServiceType = ServiceType;
	  ServiceTypeHasBeenSet = true;
   }

   public String toString()
   {
	  StringBuffer str = new StringBuffer("{");

	  str.append("sortName=" + getSortName() + " " + "autodiscoveryZombie=" + getAutodiscoveryZombie() + " " + "serviceRt=" + getServiceRt() + " " + "endUserRt=" + getEndUserRt() + " " + "modifiedBy=" + getModifiedBy() + " " + "owner=" + getOwner() + " " + "location=" + getLocation() + " " + "configResponseId=" + getConfigResponseId() + " " + "parentId=" + getParentId() + " " + "name=" + getName() + " " + "description=" + getDescription() + " " + "id=" + getId() + " " + "mTime=" + getMTime() + " " + "cTime=" + getCTime());
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
	  if (other instanceof ServiceValue)
	  {
		 ServiceValue that = (ServiceValue) other;
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
	  if (other instanceof ServiceValue)
	  {
		 ServiceValue that = (ServiceValue) other;
		 boolean lEquals = true;
		 if( this.sortName == null )
		 {
			lEquals = lEquals && ( that.sortName == null );
		 }
		 else
		 {
			lEquals = lEquals && this.sortName.equals( that.sortName );
		 }
		 lEquals = lEquals && this.autodiscoveryZombie == that.autodiscoveryZombie;
		 lEquals = lEquals && this.serviceRt == that.serviceRt;
		 lEquals = lEquals && this.endUserRt == that.endUserRt;
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
		 if( this.configResponseId == null )
		 {
			lEquals = lEquals && ( that.configResponseId == null );
		 }
		 else
		 {
			lEquals = lEquals && this.configResponseId.equals( that.configResponseId );
		 }
		 if( this.parentId == null )
		 {
			lEquals = lEquals && ( that.parentId == null );
		 }
		 else
		 {
			lEquals = lEquals && this.parentId.equals( that.parentId );
		 }
		 if( this.name == null )
		 {
			lEquals = lEquals && ( that.name == null );
		 }
		 else
		 {
			lEquals = lEquals && this.name.equals( that.name );
		 }
		 if( this.description == null )
		 {
			lEquals = lEquals && ( that.description == null );
		 }
		 else
		 {
			lEquals = lEquals && this.description.equals( that.description );
		 }
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
		 if( this.Server == null )
		 {
			lEquals = lEquals && ( that.Server == null );
		 }
		 else
		 {
			lEquals = lEquals && this.Server.equals( that.Server );
		 }
		 if( this.ServiceCluster == null )
		 {
			lEquals = lEquals && ( that.ServiceCluster == null );
		 }
		 else
		 {
			lEquals = lEquals && this.ServiceCluster.equals( that.ServiceCluster );
		 }
		 if( this.ServiceType == null )
		 {
			lEquals = lEquals && ( that.ServiceType == null );
		 }
		 else
		 {
			lEquals = lEquals && this.ServiceType.equals( that.ServiceType );
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

      result = 37*result + (autodiscoveryZombie ? 0 : 1);

      result = 37*result + (serviceRt ? 0 : 1);

      result = 37*result + (endUserRt ? 0 : 1);

      result = 37*result + ((this.modifiedBy != null) ? this.modifiedBy.hashCode() : 0);

      result = 37*result + ((this.owner != null) ? this.owner.hashCode() : 0);

      result = 37*result + ((this.location != null) ? this.location.hashCode() : 0);

      result = 37*result + ((this.configResponseId != null) ? this.configResponseId.hashCode() : 0);

      result = 37*result + ((this.parentId != null) ? this.parentId.hashCode() : 0);

      result = 37*result + ((this.name != null) ? this.name.hashCode() : 0);

      result = 37*result + ((this.description != null) ? this.description.hashCode() : 0);

      result = 37*result + ((this.id != null) ? this.id.hashCode() : 0);

      result = 37*result + ((this.mTime != null) ? this.mTime.hashCode() : 0);

      result = 37*result + ((this.cTime != null) ? this.cTime.hashCode() : 0);

	  result = 37*result + ((this.Server != null) ? this.Server.hashCode() : 0);
	  result = 37*result + ((this.ServiceCluster != null) ? this.ServiceCluster.hashCode() : 0);
	  result = 37*result + ((this.ServiceType != null) ? this.ServiceType.hashCode() : 0);
	  return result;
   }

}
