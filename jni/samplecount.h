#ifndef __TYPES_H__
#define __TYPES_H__

#include <algorithm>

// ----------------------------------------------------------------------------
// A native 64-bit integer...used when referring to any number of samples
// ----------------------------------------------------------------------------

class sampleCount
{
public:
   using type = long long;
   static_assert(sizeof(type) == 8, "Wrong width of sampleCount");

   sampleCount () : value { 0 } {}

   // Allow implicit conversion from integral types
   sampleCount ( type v ) : value { v } {}
   sampleCount ( unsigned long long v ) : value ( v ) {}
   sampleCount ( int v ) : value { v } {}
   sampleCount ( unsigned v ) : value { v } {}
   sampleCount ( long v ) : value { v } {}

   // unsigned long is 64 bit on some platforms.  Let it narrow.
   sampleCount ( unsigned long v ) : value ( v ) {}

   // Beware implicit conversions from floating point values!
   // Otherwise the meaning of binary operators with sampleCount change
   // their meaning when sampleCount is not an alias!
   explicit sampleCount ( float f ) : value ( f ) {}
   explicit sampleCount ( double d ) : value ( d ) {}

   sampleCount ( const sampleCount& ) = default;
   sampleCount &operator= ( const sampleCount& ) = default;

   float as_float() const { return value; }
   double as_double() const { return value; }

   long long as_long_long() const { return value; }

   size_t as_size_t() const {
      //wxASSERT(value >= 0);
      //wxASSERT(static_cast<std::make_unsigned<type>::type>(value) <= std::numeric_limits<size_t>::max());
      return value;
   }

   sampleCount &operator += (sampleCount b) { value += b.value; return *this; }
   sampleCount &operator -= (sampleCount b) { value -= b.value; return *this; }
   sampleCount &operator *= (sampleCount b) { value *= b.value; return *this; }
   sampleCount &operator /= (sampleCount b) { value /= b.value; return *this; }
   sampleCount &operator %= (sampleCount b) { value %= b.value; return *this; }

   sampleCount operator - () const { return -value; }

   sampleCount &operator ++ () { ++value; return *this; }
   sampleCount operator ++ (int)
      { sampleCount result{ *this }; ++value; return result; }

   sampleCount &operator -- () { --value; return *this; }
   sampleCount operator -- (int)
      { sampleCount result{ *this }; --value; return result; }

private:
   type value;
};

inline bool operator == (sampleCount a, sampleCount b)
{
   return a.as_long_long() == b.as_long_long();
}

inline bool operator != (sampleCount a, sampleCount b)
{
   return !(a == b);
}

inline bool operator < (sampleCount a, sampleCount b)
{
   return a.as_long_long() < b.as_long_long();
}

inline bool operator >= (sampleCount a, sampleCount b)
{
   return !(a < b);
}

inline bool operator > (sampleCount a, sampleCount b)
{
   return b < a;
}

inline bool operator <= (sampleCount a, sampleCount b)
{
   return !(b < a);
}

inline sampleCount operator + (sampleCount a, sampleCount b)
{
   return sampleCount{ a } += b;
}

inline sampleCount operator - (sampleCount a, sampleCount b)
{
   return sampleCount{ a } -= b;
}

inline sampleCount operator * (sampleCount a, sampleCount b)
{
   return sampleCount{ a } *= b;
}

inline sampleCount operator / (sampleCount a, sampleCount b)
{
   return sampleCount{ a } /= b;
}

inline sampleCount operator % (sampleCount a, sampleCount b)
{
   return sampleCount{ a } %= b;
}

// ----------------------------------------------------------------------------
// Function returning the minimum of a sampleCount and a size_t,
// hiding the casts
// ----------------------------------------------------------------------------

inline size_t limitSampleBufferSize( size_t bufferSize, sampleCount limit )
{
   return
      std::min( sampleCount( bufferSize ), std::max( sampleCount(0), limit ) )
         .as_size_t();
}

#endif