#ifndef __JUTE_H__
#define __JUTE_H__

#include <iostream>
#include <vector>
#include <map>
#include <string>
#include <sstream>
#include <fstream>
#include <cstring>

namespace jute {
    enum jType {JSTRING, JOBJECT, JARRAY, JBOOLEAN, JNUMBER, JNULL, JUNKNOWN};
    class jValue {
    private:
        std::string makesp(int);
        std::string svalue;
        jType type;
        std::vector<std::pair<std::string, jValue> > properties;
        std::map<std::string, size_t> mpindex;
        std::vector<jValue> arr;
        std::string to_string_d(int);
    public:
        jValue();
        jValue(jType);
        std::string to_string();
        jType get_type();
        void set_type(jType);
        void add_property(std::string key, jValue v);
        void add_element(jValue v);
        void set_string(std::string s);
        int as_int();
        long int as_long();
        double as_double();
        bool as_bool();
        void* as_null();
        std::string as_string();
        int size();
        jValue operator[](int i);
        jValue operator[](std::string s);
    };

    class parser {
    private:
        enum token_type {UNKNOWN, STRING, NUMBER, CROUSH_OPEN, CROUSH_CLOSE, BRACKET_OPEN, BRACKET_CLOSE, COMMA, COLON, BOOLEAN, NUL};

        struct token;
        static bool is_whitespace(const char c);
        static int next_whitespace(const std::string& source, int i);
        static int skip_whitespaces(const std::string& source, int i);

        static std::vector<token> tokenize(std::string source);
        static jValue json_parse(const std::vector<token>& v, int i, int& r);
    public:
        static jValue parse(const std::string& str);
        static jValue parse_file(const std::string& str);
    };
}
#endif