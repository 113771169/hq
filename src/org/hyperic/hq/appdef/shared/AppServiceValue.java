/*
 * xdoclet generated code.
 * legacy DTO pattern (targeted to be replaced with hibernate pojo).
 */
package org.hyperic.hq.appdef.shared;

/**
 * Value object for AppService.
 *
 */
public class AppServiceValue
   extends java.lang.Object
   implements java.io.Serializable
{
   private boolean isCluster;
   private boolean isClusterHasBeenSet = false;
   private boolean isEntryPoint;
   private boolean isEntryPointHasBeenSet = false;
   private java.lang.Integer id;
   private boolean idHasBeenSet = false;
   private java.lang.Long mTime;
   private boolean mTimeHasBeenSet = false;
   private java.lang.Long cTime;
   private boolean cTimeHasBeenSet = false;
   private org.hyperic.hq.appdef.shared.ServiceLightValue Service;
   private boolean ServiceHasBeenSet = false;
   private org.hyperic.hq.appdef.shared.ServiceClusterValue ServiceCluster;
   private boolean ServiceClusterHasBeenSet = false;
   private org.hyperic.hq.appdef.shared.ServiceTypeValue ServiceType;
   private boolean ServiceTypeHasBeenSet = false;

   private org.hyperic.hq.appdef.shared.AppServicePK pk;

   public AppServiceValue()
   {
	  pk = new org.hyperic.hq.appdef.shared.AppServicePK();
   }

   public AppServiceValue( boolean isCluster,boolean isEntryPoint,java.lang.Integer id,java.lang.Long mTime,java.lang.Long cTime )
   {
	  this.isCluster = isCluster;
	  isClusterHasBeenSet = true;
	  this.isEntryPoint = isEntryPoint;
	  isEntryPointHasBeenSet = true;
	  this.id = id;
	  idHasBeenSet = true;
	  this.mTime = mTime;
	  mTimeHasBeenSet = true;
	  this.cTime = cTime;
	  cTimeHasBeenSet = true;
	  pk = new org.hyperic.hq.appdef.shared.AppServicePK(this.getId());
   }

   //TODO Cloneable is better than this !
   public AppServiceValue( AppServiceValue otherValue )
   {
	  this.isCluster = otherValue.isCluster;
	  isClusterHasBeenSet = true;
	  this.isEntryPoint = otherValue.isEntryPoint;
	  isEntryPointHasBeenSet = true;
	  this.id = otherValue.id;
	  idHasBeenSet = true;
	  this.mTime = otherValue.mTime;
	  mTimeHasBeenSet = true;
	  this.cTime = otherValue.cTime;
	  cTimeHasBeenSet = true;
	// TODO Clone is better no ?
	  this.Service = otherValue.Service;
	  ServiceHasBeenSet = true;
	// TODO Clone is better no ?
	  this.ServiceCluster = otherValue.ServiceCluster;
	  ServiceClusterHasBeenSet = true;
	// TODO Clone is better no ?
	  this.ServiceType = otherValue.ServiceType;
	  ServiceTypeHasBeenSet = true;

	  pk = new org.hyperic.hq.appdef.shared.AppServicePK(this.getId());
   }

   public org.hyperic.hq.appdef.shared.AppServicePK getPrimaryKey()
   {
	  return pk;
   }

   public boolean getIsCluster()
   {
	  return this.isCluster;
   }

   public void setIsCluster( boolean isCluster )
   {
	  this.isCluster = isCluster;
	  isClusterHasBeenSet = true;

   }

   public boolean isClusterHasBeenSet(){
	  return isClusterHasBeenSet;
   }
   public boolean getIsEntryPoint()
   {
	  return this.isEntryPoint;
   }

   public void setIsEntryPoint( boolean isEntryPoint )
   {
	  this.isEntryPoint = isEntryPoint;
	  isEntryPointHasBeenSet = true;

   }

   public boolean isEntryPointHasBeenSet(){
	  return isEntryPointHasBeenSet;
   }
   public java.lang.Integer getId()
   {
	  return this.id;
   }

   public void setId( java.lang.Integer id )
   {
	  this.id = id;
	  idHasBeenSet = true;

		 pk.setId(id);
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

   public org.hyperic.hq.appdef.shared.ServiceLightValue getService()
   {
	  return this.Service;
   }
   public void setService( org.hyperic.hq.appdef.shared.ServiceLightValue Service )
   {
	  this.Service = Service;
	  ServiceHasBeenSet = true;
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

	  str.append("isCluster=" + getIsCluster() + " " + "isEntryPoint=" + getIsEntryPoint() + " " + "id=" + getId() + " " + "mTime=" + getMTime() + " " + "cTime=" + getCTime());
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
	  if (other instanceof AppServiceValue)
	  {
		 AppServiceValue that = (AppServiceValue) other;
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
	  if (other instanceof AppServiceValue)
	  {
		 AppServiceValue that = (AppServiceValue) other;
		 boolean lEquals = true;
		 lEquals = lEquals && this.isCluster == that.isCluster;
		 lEquals = lEquals && this.isEntryPoint == that.isEntryPoint;
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
		 if( this.Service == null )
		 {
			lEquals = lEquals && ( that.Service == null );
		 }
		 else
		 {
			lEquals = lEquals && this.Service.equals( that.Service );
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
      result = 37*result + (isCluster ? 0 : 1);

      result = 37*result + (isEntryPoint ? 0 : 1);

      result = 37*result + ((this.id != null) ? this.id.hashCode() : 0);

      result = 37*result + ((this.mTime != null) ? this.mTime.hashCode() : 0);

      result = 37*result + ((this.cTime != null) ? this.cTime.hashCode() : 0);

	  result = 37*result + ((this.Service != null) ? this.Service.hashCode() : 0);
	  result = 37*result + ((this.ServiceCluster != null) ? this.ServiceCluster.hashCode() : 0);
	  result = 37*result + ((this.ServiceType != null) ? this.ServiceType.hashCode() : 0);
	  return result;
   }

}
